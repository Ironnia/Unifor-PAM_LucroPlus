package br.com.lucroplus.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun init() {
        val jdbcUrl = System.getenv("DB_URL") 
            ?: "jdbc:mysql://localhost:3306/lucroplus_db?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"
        val user = System.getenv("DB_USER") ?: "root"
        val primaryPass = System.getenv("DB_PASSWORD") ?: "123"
        val fallbackPass = if (primaryPass == "123") "root" else "123"

        var connected = false
        for (pass in listOf(primaryPass, fallbackPass)) {
            try {
                val config = HikariConfig().apply {
                    this.jdbcUrl = jdbcUrl
                    this.driverClassName = "com.mysql.cj.jdbc.Driver"
                    this.username = user
                    this.password = pass
                    this.maximumPoolSize = 10
                    this.isAutoCommit = false
                    this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                    this.validate()
                }
                val dataSource = HikariDataSource(config)
                Database.connect(dataSource)
                log.info("Conexão MySQL inicializada com sucesso em: $jdbcUrl")
                connected = true
                break
            } catch (e: Exception) {
                log.warn("Tentativa de conexão com senha '$pass' falhou: ${e.message}")
            }
        }

        if (connected) {
            try {
                transaction {
                    SchemaUtils.createMissingTablesAndColumns(
                        UsuariosTable,
                        ProdutosTable,
                        IngredientesTable,
                        LotesTable,
                        FichasTecnicasTable,
                        VendasTable,
                        ItensVendaTable,
                        PromocoesTable,
                        AlertasTable,
                        AcoesAlertaTable,
                        ConfiguracoesTable
                    )

                    if (UsuariosTable.selectAll().count() == 0L) {
                        UsuariosTable.insert {
                            it[nome] = "Gerente Teste"
                            it[email] = "gerente@lucroplus.com"
                            it[senhaHash] = "123"
                            it[tipo] = "GERENTE"
                            it[ativo] = true
                        }
                    }
                }
            } catch (e: Exception) {
                log.warn("Erro ao verificar schema MySQL: ${e.message}")
            }
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
