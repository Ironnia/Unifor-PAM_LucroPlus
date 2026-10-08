package br.com.lucroplus.routes

import br.com.lucroplus.models.AlertaDto
import br.com.lucroplus.models.ErrorResponse
import br.com.lucroplus.models.LotePendentePromocaoDto
import br.com.lucroplus.services.AcaoAlerta
import br.com.lucroplus.services.AlertaOperacoes
import br.com.lucroplus.services.ResultadoAcaoAlerta
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

class AlertaRoutesTest {
    private val audience = "alertas-test"
    private val issuer = "lucroplus-test"
    private val algorithm = Algorithm.HMAC256("somente-testes-alertas-1234567890")
    private val token = JWT.create().withAudience(audience).withIssuer(issuer)
        .sign(algorithm)

    private class Fake : AlertaOperacoes {
        var resultado = ResultadoAcaoAlerta.CONCLUIDA
        var ultimaAcao: AcaoAlerta? = null
        override suspend fun obterAlertasVencimento(): List<AlertaDto> = emptyList()
        override suspend fun listarLotesPendentes(): List<LotePendentePromocaoDto> = emptyList()
        override suspend fun executarAcao(alertaId: Long, acao: AcaoAlerta): ResultadoAcaoAlerta {
            ultimaAcao = acao
            return resultado
        }
    }

    private fun Application.modulo(fake: Fake) {
        install(Authentication) {
            jwt("auth-jwt") {
                realm = "LucroPlus Test"
                verifier(JWT.require(algorithm).withAudience(audience).withIssuer(issuer).build())
                validate { credential -> JWTPrincipal(credential.payload) }
                challenge { _, _ ->
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Token inválido"))
                }
            }
        }
        install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
        routing {
            alertaRoutes(fake)
            promocaoRoutes(fake)
        }
    }

    @Test
    fun `rotas de alerta e fila exigem token`() = testApplication {
        application { modulo(Fake()) }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/alertas/vencimento").status)
        assertEquals(HttpStatusCode.Unauthorized, client.patch("/alertas/1/salvar-lote").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/promocoes/pendentes").status)
    }

    @Test
    fun `acoes devolvem codigos coerentes e alias antigo significa ciente`() = testApplication {
        val fake = Fake()
        application { modulo(fake) }

        val invalido = client.patch("/alertas/abc/salvar-lote") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, invalido.status)

        val salvo = client.patch("/alertas/1/salvar-lote") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, salvo.status)
        assertEquals(AcaoAlerta.SALVAR_LOTE, fake.ultimaAcao)

        fake.resultado = ResultadoAcaoAlerta.CONFLITO
        val conflito = client.patch("/alertas/1/ciente") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Conflict, conflito.status)
        assertEquals(AcaoAlerta.CIENTE, fake.ultimaAcao)

        fake.resultado = ResultadoAcaoAlerta.NAO_ENCONTRADO
        val ausente = client.patch("/alertas/999/visualizar") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, ausente.status)
        assertEquals(AcaoAlerta.CIENTE, fake.ultimaAcao)

        val pendentes = client.get("/promocoes/pendentes") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, pendentes.status)
        assertTrue(pendentes.bodyAsText().contains("[]"))
    }
}
