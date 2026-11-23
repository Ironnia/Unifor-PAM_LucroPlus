package br.com.lucroplus.services

import br.com.lucroplus.database.*
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.math.BigDecimal
import java.sql.DriverManager
import java.time.LocalDate
import java.util.UUID

class FifoFixture(val cenario: String? = null) : AutoCloseable {
    val mysql = System.getenv("LUCROPLUS_FIFO_MYSQL") == "true"
    val nome = if (cenario == null) "fifo_test_" + UUID.randomUUID().toString().replace("-", "")
        else "fifo_lucroplus_" + UUID.randomUUID().toString().replace("-", "") + "_" + cenario
    private val parametros = "?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&preserveInstants=true"
    private val url = "jdbc:mysql://127.0.0.1:${System.getenv("LUCROPLUS_TEST_MYSQL_PORT") ?: "3306"}/"
    val banco: Database

    init {
        require(cenario == null || cenario in listOf("parcial", "integral", "perda", "travessia", "inicio_efetivo", "inicio_futuro", "historico"))
        if (mysql) {
            DriverManager.getConnection(url + parametros, System.getenv("DB_USER"), System.getenv("DB_PASSWORD")).use {
                it.createStatement().use { st -> st.executeUpdate("CREATE DATABASE `$nome` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci") }
            }
            banco = Database.connect(url + nome + parametros, "com.mysql.cj.jdbc.Driver", System.getenv("DB_USER"), System.getenv("DB_PASSWORD"))
        } else banco = Database.connect("jdbc:h2:mem:$nome;MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver")
        TransactionManager.defaultDatabase = banco
        try {
            if (cenario != null) {
                require(mysql) { "Replay da massa FIFO exige MySQL real" }
                carregar("homologacao_fifo_esquema.sql")
                carregar("homologacao_fifo_lucroplus.sql")
            } else transaction(banco) {
                SchemaUtils.create(RestaurantesTable, UsuariosTable, IngredientesTable, ProdutosTable, LotesTable,
                    FichasTecnicasTable, VendasTable, ItensVendaTable, PromocoesTable, MovimentosEstoqueTable)
                for (loja in 1L..2L) {
                    RestaurantesTable.insert { it[id] = loja; it[nome] = "Loja $loja" }
                    IngredientesTable.insert {
                        it[id] = loja; it[restauranteId] = loja; it[nome] = "Tomate"
                        it[idExterno] = "tomate"; it[unidade] = "g"
                    }
                    ProdutosTable.insert {
                        it[id] = loja; it[restauranteId] = loja; it[nome] = "Prato"
                        it[idExterno] = "prato"; it[preco] = BigDecimal("20"); it[categoria] = "Teste"
                    }
                    FichasTecnicasTable.insert {
                        it[produtoId] = loja; it[ingredienteId] = loja
                        it[quantidadeUsada] = BigDecimal("200"); it[unidade] = "g"
                    }
                }
            }
        } catch (e: Exception) { close(); throw e }
    }

    fun carregar(arquivo: String) {
        val processo = ProcessBuilder("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe",
            "--host=127.0.0.1", "--port=" + (System.getenv("LUCROPLUS_TEST_MYSQL_PORT") ?: "3306"), "--protocol=TCP", "--user=" + System.getenv("DB_USER"),
            "--default-character-set=utf8mb4", "--batch", nome).apply {
            environment()["MYSQL_PWD"] = System.getenv("DB_PASSWORD")
            redirectErrorStream(true)
        }.start()
        processo.outputStream.use { it.write(("SET time_zone='+00:00'; SET @fifo_cenario='$cenario';\n" +
            File("../database/$arquivo").readText(Charsets.UTF_8)).toByteArray(Charsets.UTF_8)) }
        val saida = processo.inputStream.bufferedReader(Charsets.UTF_8).readText()
        check(processo.waitFor() == 0) { "Carga fictícia recusada: $saida" }
    }

    fun lote(id: Long = 1, gramas: Int = 10000, entrada: String = "2026-09-30T08:00:00-03:00",
             validade: String = "2026-10-09", loja: Long = 1, ingrediente: Long = loja) = transaction(banco) {
        LotesTable.insert {
            it[LotesTable.id] = id; it[restauranteId] = loja; it[ingredienteId] = ingrediente
            it[quantidadeG] = gramas; it[quantidadeInicialG] = gramas; it[custoUnitario] = BigDecimal("0.006")
            it[entradaEm] = InstantesEstoque.parse(entrada)
            it[dataEntrada] = InstantesEstoque.dataCivil(InstantesEstoque.parse(entrada)).toKotlinLocalDate()
            it[dataValidade] = LocalDate.parse(validade).toKotlinLocalDate()
        }
    }

    fun venda(id: Long = 1, quantidade: Int = 1, horario: String = "2026-10-01T10:00:00-03:00",
              loja: Long = 1, produto: Long = loja) = transaction(banco) {
        VendasTable.insert {
            it[VendasTable.id] = id; it[restauranteId] = loja; it[idExterno] = "v$id"; it[origem] = "pdv"
            it[dataVendaEm] = InstantesEstoque.parse(horario)
            it[dataVenda] = InstantesEstoque.dataCivil(InstantesEstoque.parse(horario)).toKotlinLocalDate()
            it[valorTotal] = BigDecimal(quantidade * 20)
        }
        item(id, id, produto, quantidade)
    }

    fun item(id: Long, venda: Long, produto: Long, quantidade: Int) = transaction(banco) {
        ItensVendaTable.insert {
            it[ItensVendaTable.id] = id; it[vendaId] = venda; it[produtoId] = produto
            it[ItensVendaTable.quantidade] = quantidade; it[precoUnitario] = BigDecimal("20")
        }
    }

    fun consumir(venda: Long = 1, loja: Long = 1) = transaction(banco) {
        MovimentoEstoqueService(loja).consumirVendaNaTransacao(venda)
    }
    fun saldo(id: Long = 1) = transaction(banco) { LotesTable.selectAll().where { LotesTable.id eq id }.single()[LotesTable.quantidadeG] }
    fun movimentos() = transaction(banco) { MovimentosEstoqueTable.selectAll().count().toInt() }

    override fun close() {
        TransactionManager.closeAndUnregister(banco)
        if (mysql) {
            require(nome.matches(Regex("fifo_test_[a-f0-9]{32}|fifo_lucroplus_[a-f0-9]{32}_(parcial|integral|perda|travessia|inicio_efetivo|inicio_futuro|historico)")))
            DriverManager.getConnection(url + parametros, System.getenv("DB_USER"), System.getenv("DB_PASSWORD")).use {
                it.createStatement().use { st -> st.executeUpdate("DROP DATABASE `$nome`") }
            }
        }
    }
}
