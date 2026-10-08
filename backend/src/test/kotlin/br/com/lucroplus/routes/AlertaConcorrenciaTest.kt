package br.com.lucroplus.routes

import br.com.lucroplus.database.*
import br.com.lucroplus.models.AlertaDto
import br.com.lucroplus.services.AlertaService
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDate
import java.util.UUID
import kotlin.test.*

// teste com banco temporario caso variavel esteja configurada
class AlertaConcorrenciaTest {
    private val nome = "test_alertas_" + UUID.randomUUID().toString().replace("-", "")
    private val mysql = System.getenv("LUCROPLUS_ALERTAS_MYSQL") == "true"
    private val url = "jdbc:mysql://127.0.0.1:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    private lateinit var banco: Database
    private val algorithm = Algorithm.HMAC256("chave-ficticia-exclusiva-deste-teste")
    private val token = JWT.create().withAudience("teste").withIssuer("teste").sign(algorithm)

    @BeforeTest
    fun preparar() {
        if (mysql) DriverManager.getConnection(url, System.getenv("DB_USER"), System.getenv("DB_PASSWORD")).use {
            it.createStatement().use { s -> s.executeUpdate("CREATE DATABASE $nome") }
        }
        val config = DatabaseConfig { defaultIsolationLevel = Connection.TRANSACTION_REPEATABLE_READ }
        banco = if (mysql) Database.connect(url.replace("/?", "/$nome?"), "com.mysql.cj.jdbc.Driver",
            System.getenv("DB_USER"), System.getenv("DB_PASSWORD"), databaseConfig = config)
        else Database.connect("jdbc:h2:mem:$nome;MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver", databaseConfig = config)
        TransactionManager.defaultDatabase = banco
        transaction(banco) {
            SchemaUtils.create(IngredientesTable, ProdutosTable, LotesTable, AlertasTable, AcoesAlertaTable, PromocoesTable)
            IngredientesTable.insert { it[id] = 1; it[nome] = "Ingrediente fictício"; it[unidade] = "g" }
            for (idLote in 1L..3L) LotesTable.insert {
                it[id] = idLote; it[ingredienteId] = 1; it[quantidadeG] = 1000
                it[custoUnitario] = java.math.BigDecimal("0.01")
                it[dataEntrada] = LocalDate.now().toKotlinLocalDate()
                it[dataValidade] = LocalDate.now().plusDays(idLote + 1).toKotlinLocalDate()
            }
        }
    }

    @AfterTest
    fun limpar() {
        if (mysql) DriverManager.getConnection(url, System.getenv("DB_USER"), System.getenv("DB_PASSWORD")).use {
            it.createStatement().use { s -> s.executeUpdate("DROP DATABASE $nome") }
        }
    }

    private fun Application.modulo() {
        install(ContentNegotiation) { json() }
        install(Authentication) {
            jwt("auth-jwt") {
                verifier(JWT.require(algorithm).withAudience("teste").withIssuer("teste").build())
                validate { JWTPrincipal(it.payload) }
            }
        }
        routing { alertaRoutes(AlertaService); promocaoRoutes(AlertaService) }
    }

    @Test
    fun `consultas HTTP simultaneas criam um alerta por lote e mantem os IDs`() = testApplication {
        application { modulo() }
        startApplication()
        val respostas = coroutineScope {
            val inicio = CompletableDeferred<Unit>()
            val tarefas = (1..12).map { async(Dispatchers.Default) {
                inicio.await()
                val r = client.get("/alertas/vencimento") { bearerAuth(token) }
                assertEquals(HttpStatusCode.OK, r.status)
                Json.decodeFromString<List<AlertaDto>>(r.bodyAsText())
            } }
            inicio.complete(Unit)
            tarefas.awaitAll()
        }
        val ids = respostas.first().map { it.id }.toSet()
        respostas.forEach { assertEquals(3, it.size); assertEquals(ids, it.map { a -> a.id }.toSet()) }
        assertEquals(3L, transaction(banco) { AlertasTable.selectAll().count() })
        val repetida = client.get("/alertas/vencimento") { bearerAuth(token) }
        assertEquals(ids, Json.decodeFromString<List<AlertaDto>>(repetida.bodyAsText()).map { it.id }.toSet())
    }

    @Test
    fun `acoes HTTP concorrentes sao idempotentes e nao reabrem alerta`() = testApplication {
        application { modulo() }
        startApplication()
        val lista = AlertaService.obterAlertasVencimento()
        for ((alerta, acao) in lista.take(2).zip(listOf("salvar-lote", "ciente"))) {
            val respostas = coroutineScope {
                (1..8).map { async(Dispatchers.Default) {
                    client.patch("/alertas/${alerta.id}/$acao") { bearerAuth(token) }.status
                } }.awaitAll()
            }
            assertTrue(respostas.all { it == HttpStatusCode.OK }, respostas.toString())
            val oposta = if (acao == "ciente") "salvar-lote" else "ciente"
            assertEquals(HttpStatusCode.Conflict, client.patch("/alertas/${alerta.id}/$oposta") { bearerAuth(token) }.status)
        }
        assertEquals(1, AlertaService.obterAlertasVencimento().size)
        assertEquals(1, AlertaService.listarLotesPendentes().size)
        assertEquals(2L, transaction(banco) { AcoesAlertaTable.selectAll().count() })
    }

    @Test
    fun `banco rejeita mesmo lote e tipo mas aceita outro tipo`() = runBlocking {
        val alerta = AlertaService.obterAlertasVencimento().first()
        fun inserir(tipoAlerta: String) = transaction(banco) {
            maxAttempts = 1
            AlertasTable.insert {
                it[loteId] = requireNotNull(alerta.loteId); it[tipo] = tipoAlerta; it[mensagem] = "Teste"
                it[dataAlerta] = LocalDate.now().toKotlinLocalDate()
            }
        }
        assertFailsWith<org.jetbrains.exposed.exceptions.ExposedSQLException> { inserir("VENCIMENTO") }
        inserir("ESTOQUE_MINIMO")
        assertEquals(3, AlertaService.obterAlertasVencimento().size)
    }
}
