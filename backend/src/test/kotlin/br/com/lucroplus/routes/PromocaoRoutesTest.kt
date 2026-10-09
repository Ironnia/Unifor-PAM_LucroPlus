package br.com.lucroplus.routes

import br.com.lucroplus.models.*
import br.com.lucroplus.services.PromocaoFixture
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
import kotlin.test.*

class PromocaoRoutesTest {
    @Test
    fun `Netty real preserva previa e persiste criar ativar recusar e descartar`() {
        val f = PromocaoFixture()
        val servidor = io.ktor.server.engine.embeddedServer(io.ktor.server.netty.Netty,
            host = "127.0.0.1", port = 0) { modulo(f) }.start(wait = false)
        try {
            val porta = kotlinx.coroutines.runBlocking { servidor.resolvedConnectors().single().port }
            val http = java.net.http.HttpClient.newHttpClient()
            fun chamar(metodo: String, path: String, corpo: String? = null, auth: Boolean = true): java.net.http.HttpResponse<String> {
                val pedido = java.net.http.HttpRequest.newBuilder(java.net.URI("http://127.0.0.1:$porta$path"))
                if (auth) pedido.header("Authorization", "Bearer $token")
                pedido.header("Content-Type", "application/json")
                pedido.method(metodo, corpo?.let { java.net.http.HttpRequest.BodyPublishers.ofString(it) }
                    ?: java.net.http.HttpRequest.BodyPublishers.noBody())
                return http.send(pedido.build(), java.net.http.HttpResponse.BodyHandlers.ofString())
            }
            assertEquals(401, chamar("GET", "/promocoes/previas", auth = false).statusCode())
            assertEquals(200, chamar("GET", "/promocoes/previas").statusCode())
            assertEquals("[]", chamar("GET", "/promocoes").body())
            val p = json.decodeFromString<PromocaoDto>(chamar("POST", "/promocoes", """{"loteId":1,"pratoId":1}""").body())
            assertEquals(StatusPromocao.SUGERIDA, p.status)
            assertEquals(400, chamar("PATCH", "/promocoes/${p.id}/ativar",
                """{"descontoPct":20,"dataInicio":"${f.hoje}","dataFim":"${f.hoje.plusDays(7)}"}""").statusCode())
            val dados = """{"descontoPct":20,"dataInicio":"${f.hoje}","dataFim":"${f.hoje.plusDays(6)}"}"""
            repeat(2) { assertEquals(200, chamar("PATCH", "/promocoes/${p.id}/ativar", dados).statusCode()) }
            assertEquals(1, json.decodeFromString<List<PromocaoDto>>(chamar("GET", "/promocoes?status=EM_ANDAMENTO").body()).size)
            assertEquals(409, chamar("PATCH", "/promocoes/pendentes/1/descartar").statusCode())
            assertEquals(409, chamar("PATCH", "/promocoes/${p.id}/confirmar", """{"loteEscoado":true}""").statusCode())
            f.lote(20, 7)
            val recusar = json.decodeFromString<PromocaoDto>(chamar("POST", "/promocoes", """{"loteId":20,"pratoId":1}""").body())
            repeat(2) { assertEquals(200, chamar("PATCH", "/promocoes/${recusar.id}/recusar").statusCode()) }
            f.lote(21, 7)
            repeat(2) { assertEquals(200, chamar("PATCH", "/promocoes/pendentes/21/descartar").statusCode()) }
            assertEquals(400, chamar("PATCH", "/promocoes/pendentes/x/descartar").statusCode())
            assertEquals(404, chamar("PATCH", "/promocoes/pendentes/999/descartar").statusCode())
            assertEquals(2, json.decodeFromString<List<PromocaoDto>>(chamar("GET", "/promocoes").body()).size)
        } finally { servidor.stop(100, 1000) }
    }

    private val algoritmo = Algorithm.HMAC256("chave-ficticia-somente-para-testes-promocao")
    private val token = JWT.create().withAudience("teste").withIssuer("teste").sign(algoritmo)
    private val json = Json { ignoreUnknownKeys = true }

    private fun Application.modulo(f: PromocaoFixture) {
        install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
        install(Authentication) {
            jwt("auth-jwt") {
                verifier(JWT.require(algoritmo).withAudience("teste").withIssuer("teste").build())
                validate { JWTPrincipal(it.payload) }
            }
        }
        routing { promocaoRoutes(ciclo = f.servico) }
    }

    private fun HttpRequestBuilder.autenticado(corpo: String? = null) {
        bearerAuth(token)
        if (corpo != null) { contentType(ContentType.Application.Json); setBody(corpo) }
    }

    @Test
    fun `todas as rotas exigem JWT inclusive PATCHs antigos`() = testApplication {
        application { modulo(PromocaoFixture()) }
        for (path in listOf("/promocoes", "/promocoes/resumo", "/promocoes/sugestoes", "/promocoes/pendentes", "/promocoes/previas")) {
            assertEquals(HttpStatusCode.Unauthorized, client.get(path).status)
        }
        assertEquals(HttpStatusCode.Unauthorized, client.patch("/promocoes/pendentes/1/descartar").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/promocoes").status)
        for (acao in listOf("ativar", "recusar", "confirmar")) {
            assertEquals(HttpStatusCode.Unauthorized, client.patch("/promocoes/1/$acao").status)
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/promocoes") { bearerAuth("invalido") }.status)
    }

    @Test
    fun `HTTP e banco percorrem sugestao ativacao confirmacao e resumo`() = testApplication {
        val f = PromocaoFixture()
        application { modulo(f) }
        val resposta = client.post("/promocoes") { autenticado("""{"loteId":1,"pratoId":1}""") }
        assertEquals(HttpStatusCode.OK, resposta.status)
        val p = json.decodeFromString<PromocaoDto>(resposta.bodyAsText())
        val dados = """{"descontoPct":20,"dataInicio":"${f.hoje}","dataFim":"${f.hoje}"}"""
        assertEquals(HttpStatusCode.OK, client.patch("/promocoes/${p.id}/ativar") { autenticado(dados) }.status)
        val lista = client.get("/promocoes?status=EM_ANDAMENTO") { autenticado() }
        assertEquals(1, json.decodeFromString<List<PromocaoDto>>(lista.bodyAsText()).size)
        assertEquals(HttpStatusCode.Conflict, client.patch("/promocoes/${p.id}/confirmar") {
            autenticado("""{"loteEscoado":true}""")
        }.status)
        f.hoje = f.hoje.plusDays(1)
        repeat(2) {
            assertEquals(HttpStatusCode.OK, client.patch("/promocoes/${p.id}/confirmar") {
                autenticado("""{"loteEscoado":true}""")
            }.status)
        }
        val resumo = client.get("/promocoes/resumo") { autenticado() }
        assertEquals("15.18", json.decodeFromString<ResumoPromocoesDto>(resumo.bodyAsText()).prejuizoEvitado)
    }

    @Test
    fun `JSON incompleto ids e status invalidos retornam 400 e ausente 404`() = testApplication {
        application { modulo(PromocaoFixture()) }
        for (corpo in listOf("{", "{}", """{"loteId":"x","pratoId":1}""")) {
            assertEquals(HttpStatusCode.BadRequest, client.post("/promocoes") { autenticado(corpo) }.status)
        }
        assertEquals(HttpStatusCode.BadRequest, client.get("/promocoes?status=ATIVA") { autenticado() }.status)
        assertEquals(HttpStatusCode.BadRequest, client.patch("/promocoes/abc/recusar") { autenticado() }.status)
        assertEquals(HttpStatusCode.NotFound, client.patch("/promocoes/999/recusar") { autenticado() }.status)
        assertEquals(HttpStatusCode.BadRequest, client.patch("/promocoes/1/ativar") { autenticado("{}") }.status)
        assertEquals(HttpStatusCode.BadRequest, client.patch("/promocoes/1/confirmar") { autenticado("{}") }.status)
    }
}
