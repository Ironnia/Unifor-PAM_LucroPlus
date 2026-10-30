package br.com.lucroplus.routes

import br.com.lucroplus.database.*
import br.com.lucroplus.models.*
import br.com.lucroplus.services.PdvFixture
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
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.*

class ImportacaoRoutesTest {
    private val algoritmo = Algorithm.HMAC256("pdv-chave-ficticia-exclusiva-teste-http")
    private val json = Json { ignoreUnknownKeys = true }
    private fun token(id: Long) = JWT.create().withClaim("id",id).sign(algoritmo)
    @BeforeEach fun exigirMysql() { assumeTrue(System.getenv("LUCROPLUS_PDV_MYSQL") == "true") }
    private fun Application.modulo() {
        install(ContentNegotiation) { json(json) }
        install(Authentication) {
            jwt("auth-jwt") {
                verifier(JWT.require(algoritmo).build())
                validate { JWTPrincipal(it.payload) }
            }
        }
        routing { importacaoRoutes(); configuracaoRoutes() }
    }
    private fun HttpRequestBuilder.conta(id: Long) { bearerAuth(token(id)) }

    @Test fun `HTTP exige sessao usa loja do gerente e status persiste sem senha`() {
        PdvFixture().use { f -> testApplication {
            application { modulo() }
            assertEquals(HttpStatusCode.Unauthorized,client.post("/importacao/pdv").status)
            assertEquals(HttpStatusCode.Unauthorized,client.get("/importacao/pdv/status").status)
            val inicial=client.get("/importacao/pdv/status") { conta(1) }
            assertNull(json.decodeFromString<SincronizacaoDto>(inicial.bodyAsText()).ultimaSincronizacao)
            val a=client.post("/importacao/pdv") { conta(1) }
            assertEquals(HttpStatusCode.OK,a.status)
            val resultado=json.decodeFromString<ImportacaoResponse>(a.bodyAsText())
            assertTrue(resultado.sucesso); assertEquals(2,resultado.vendasNovas); assertEquals(8,resultado.movimentosNovos)
            assertEquals(3000,f.saldo()); assertNull(f.status(2))
            assertEquals(HttpStatusCode.Forbidden, client.post("/importacao/pdv?restauranteId=2") { conta(1) }.status)
            val status=client.get("/importacao/pdv/status") { conta(1) }
            assertEquals(resultado.ultimaSincronizacao,json.decodeFromString<SincronizacaoDto>(status.bodyAsText()).ultimaSincronizacao)
            val b=client.post("/importacao/pdv") { conta(2) }
            assertTrue(json.decodeFromString<ImportacaoResponse>(b.bodyAsText()).sucesso); assertEquals(9000,f.saldo(2))
            for (body in listOf(a.bodyAsText(),b.bodyAsText(),status.bodyAsText())) {
                assertFalse(body.contains("jdbc:")); assertFalse(body.contains("password")); assertFalse(body.contains(System.getenv("DB_PASSWORD")))
            }
            assertEquals(HttpStatusCode.Forbidden,client.post("/configuracoes/pdv?restauranteId=2") {
                conta(1); contentType(ContentType.Application.Json); setBody("{}")
            }.status)
            assertEquals(HttpStatusCode.Forbidden,client.get("/configuracoes/pdv?restauranteId=1") { conta(1) }.status)
            transaction(f.banco) { UsuariosTable.update({ UsuariosTable.id eq 1 }) { it[restauranteId]=null } }
            assertEquals(HttpStatusCode.Forbidden,client.post("/importacao/pdv") { conta(1) }.status)
        } }
    }

    @Test fun `HTTP informa falha sem avancar ultimo sucesso nem expor configuracao`() {
        PdvFixture().use { f -> testApplication {
            application { modulo() }
            val sucesso=client.post("/importacao/pdv") { conta(1) }
            val ultimo=json.decodeFromString<ImportacaoResponse>(sucesso.bodyAsText()).ultimaSincronizacao
            val estado=f.estado()
            f.sql("INSERT INTO pdv_venda VALUES (1,99,101,'2026-10-01 12:00:00',9999,20,199980)")
            val falha=client.post("/importacao/pdv") { conta(1) }
            val r=json.decodeFromString<ImportacaoResponse>(falha.bodyAsText())
            assertFalse(r.sucesso); assertEquals(ultimo,r.ultimaSincronizacao); assertEquals(estado,f.estado())
            transaction(f.banco) { ConfiguracoesRestauranteTable.update({
                (ConfiguracoesRestauranteTable.restauranteId eq 1) and (ConfiguracoesRestauranteTable.chave eq "pdv_url")
            }) { it[valor]="jdbc:mysql://127.0.0.1:1/indisponivel" } }
            val conexao=client.post("/importacao/pdv") { conta(1) }
            assertFalse(json.decodeFromString<ImportacaoResponse>(conexao.bodyAsText()).sucesso)
            assertFalse(conexao.bodyAsText().contains("jdbc:")); assertFalse(conexao.bodyAsText().contains(System.getenv("DB_PASSWORD")))
            assertEquals(ultimo,f.status()); assertEquals(estado,f.estado())
        } }
    }
}
