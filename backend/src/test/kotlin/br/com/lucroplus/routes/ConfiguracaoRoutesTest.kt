package br.com.lucroplus.routes

import br.com.lucroplus.models.ErrorResponse
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
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfiguracaoRoutesTest {

    private val testAudience = "lucroplus-test-users"
    private val testIssuer = "lucroplus-test"
    private val testAlgorithm = Algorithm.HMAC256("lucroplus-test-secret-with-32-characters")
    private val testVerifier = JWT.require(testAlgorithm)
        .withAudience(testAudience)
        .withIssuer(testIssuer)
        .build()
    private val token = JWT.create()
        .withAudience(testAudience)
        .withIssuer(testIssuer)
        .withClaim("id", 1L)
        .sign(testAlgorithm)

    private fun Application.configurarModuloDeTeste() {
        install(Authentication) {
            jwt("auth-jwt") {
                realm = "LucroPlus Test"
                verifier(testVerifier)
                validate { credential ->
                    if (credential.payload.audience.contains(testAudience)) {
                        JWTPrincipal(credential.payload)
                    } else {
                        null
                    }
                }
                challenge { _, _ ->
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Token inválido"))
                }
            }
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        routing {
            configuracaoRoutes()
        }
    }

    private fun HttpRequestBuilder.autorizar() {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
    }

    @Test
    fun `deve bloquear teste de conexao sem token`() = testApplication {
        application { configurarModuloDeTeste() }

        val response = client.post("/configuracoes/pdv/testar-conexao") {
            contentType(ContentType.Application.Json)
            setBody("""{"host":"simulado","usuario":"mock","senha":""}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `deve retornar sucesso no modo simulado`() = testApplication {
        application { configurarModuloDeTeste() }

        val response = client.post("/configuracoes/pdv/testar-conexao") {
            autorizar()
            setBody("""{"host":"simulado","porta":3306,"banco":"pdv","usuario":"mock","senha":""}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"sucesso\":true"))
        assertTrue(body.contains("\"tempoRespostaMs\":50"))
    }

    @Test
    fun `deve retornar bad request para porta invalida`() = testApplication {
        application { configurarModuloDeTeste() }

        val response = client.post("/configuracoes/pdv/testar-conexao") {
            autorizar()
            setBody("""{"host":"localhost","porta":70000,"usuario":"teste","senha":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("porta"))
    }

    @Test
    fun `deve retornar bad request quando a conexao falhar`() = testApplication {
        application { configurarModuloDeTeste() }

        val response = client.post("/configuracoes/pdv/testar-conexao") {
            autorizar()
            setBody("""{"host":"127.0.0.1","porta":1,"banco":"inexistente","usuario":"teste","senha":"invalida"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"sucesso\":false"))
        assertTrue(!body.contains("CommunicationsException"))
    }

    @Test
    fun `deve retornar bad request para json malformado`() = testApplication {
        application { configurarModuloDeTeste() }

        val response = client.post("/configuracoes/pdv/testar-conexao") {
            autorizar()
            setBody("{")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
