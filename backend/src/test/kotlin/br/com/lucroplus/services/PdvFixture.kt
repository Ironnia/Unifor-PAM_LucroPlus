package br.com.lucroplus.services

import br.com.lucroplus.database.*
import br.com.lucroplus.models.PdvConfigDto
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.sql.DriverManager
import java.util.UUID

class PdvFixture(val cenario: String = "parcial") : AutoCloseable {
    private val token = UUID.randomUUID().toString().replace("-", "")
    val nome = "fifo_lucroplus_${token}_$cenario"
    val origem = "fifo_pdv_${token}_$cenario"
    private val base = "jdbc:mysql://127.0.0.1:${System.getenv("LUCROPLUS_TEST_MYSQL_PORT") ?: "3306"}/"
    private val parametros = "?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&preserveInstants=true"
    private val criados = mutableListOf<String>()
    lateinit var banco: Database
        private set

    init {
        require(System.getenv("LUCROPLUS_PDV_MYSQL") == "true") { "FIFO exige opt-in MySQL" }
        require(cenario in listOf("parcial", "integral", "perda", "travessia", "inicio_efetivo", "inicio_futuro", "historico"))
        try {
            for (db in listOf(nome, origem)) {
                sql("CREATE DATABASE `$db` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci", null)
                criados += db
            }
            carregar("homologacao_fifo_esquema.sql", nome)
            carregar("homologacao_fifo_catalogo.sql", nome)
            carregar("homologacao_fifo_pdv.sql", origem)
            banco = Database.connect(base+nome+parametros, "com.mysql.cj.jdbc.Driver", System.getenv("DB_USER"), System.getenv("DB_PASSWORD"))
            TransactionManager.defaultDatabase = banco
            transaction(banco) { SchemaUtils.create(SyncPdvHistoricoTable) }
            runBlocking {
                for (loja in 1L..2L) ConfiguracaoService(loja).salvarPdvConfig(PdvConfigDto(url=base+origem+parametros,
                    username=System.getenv("DB_USER"), password=System.getenv("DB_PASSWORD")))
            }
        } catch (e: Exception) { close(); throw e }
    }

    fun sql(comando: String, db: String? = origem) {
        DriverManager.getConnection(base+(db ?: "")+parametros, System.getenv("DB_USER"), System.getenv("DB_PASSWORD")).use {
            it.createStatement().use { st -> st.execute(comando) }
        }
    }

    private fun carregar(arquivo: String, db: String) {
        val processo = ProcessBuilder("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe",
            "--host=127.0.0.1", "--port=" + (System.getenv("LUCROPLUS_TEST_MYSQL_PORT") ?: "3306"), "--protocol=TCP", "--user="+System.getenv("DB_USER"),
            "--default-character-set=utf8mb4", "--batch", db).apply {
            environment()["MYSQL_PWD"] = System.getenv("DB_PASSWORD"); redirectErrorStream(true)
        }.start()
        processo.outputStream.use { it.write(("SET time_zone='+00:00'; SET @fifo_cenario='$cenario';\n"+
            File("../database/$arquivo").readText(Charsets.UTF_8)).toByteArray(Charsets.UTF_8)) }
        val saida = processo.inputStream.bufferedReader().readText()
        check(processo.waitFor() == 0) { "Fixture FIFO recusado: $saida" }
    }

    fun importar(loja: Long = 1) = runBlocking { ImportacaoService(loja).sincronizarPdv() }
    fun status(loja: Long = 1) = runBlocking { ImportacaoService(loja).statusPdv().ultimaSincronizacao }
    fun saldo(loja: Long = 1, externo: String = "1") = transaction(banco) {
        LotesTable.selectAll().where { (LotesTable.restauranteId eq loja) and (LotesTable.idExterno eq externo) }
            .single()[LotesTable.quantidadeG]
    }
    fun contagens() = transaction(banco) { listOf(LotesTable.selectAll().count(), VendasTable.selectAll().count(),
        ItensVendaTable.selectAll().count(), MovimentosEstoqueTable.selectAll().count()) }

    fun hashOrigem(): String {
        val dados = DriverManager.getConnection(base+origem+parametros, System.getenv("DB_USER"), System.getenv("DB_PASSWORD")).use { c ->
            listOf("pdv_produto", "pdv_ficha_tecnica", "pdv_compra", "pdv_venda", "pdv_estoque").joinToString("\n") { tabela ->
                c.createStatement().use { st -> st.executeQuery("SELECT * FROM $tabela").use { rs ->
                    buildList { while (rs.next()) add((1..rs.metaData.columnCount).joinToString("|") { rs.getString(it).orEmpty() }) }
                        .sorted().joinToString("\n")
                } }
            }
        }
        return java.security.MessageDigest.getInstance("SHA-256").digest(dados.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun estado() = transaction(banco) {
        listOf(LotesTable, VendasTable, ItensVendaTable, MovimentosEstoqueTable).joinToString("\n") { tabela ->
            tabela.selectAll().map { row -> tabela.columns.joinToString("|") { row[it].toString() } }.sorted().joinToString("\n")
        }
    }

    override fun close() {
        if (::banco.isInitialized) TransactionManager.closeAndUnregister(banco)
        for (db in criados.reversed()) {
            require(db.matches(Regex("fifo_(lucroplus|pdv)_[a-f0-9]{32}_(parcial|integral|perda|travessia|inicio_efetivo|inicio_futuro|historico)")))
            sql("DROP DATABASE `$db`", null)
        }
        criados.clear()
    }
}
