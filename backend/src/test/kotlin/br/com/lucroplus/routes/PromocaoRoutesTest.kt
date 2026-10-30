package br.com.lucroplus.routes

import br.com.lucroplus.models.*
import br.com.lucroplus.database.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
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
    private val algoritmo = Algorithm.HMAC256("chave-ficticia-somente-para-testes-promocao")
    private val token = JWT.create().withClaim("id", 1L).withAudience("teste").withIssuer("teste").sign(algoritmo)
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
    fun `GET serializa avisos independentes preservando observacao sem criar campanha`() = testApplication {
        val f = PromocaoFixture()
        transaction(f.banco) {
            IngredientesTable.insert {
                it[IngredientesTable.restauranteId] = 1L; it[id] = 10L; it[nome] = "Alface"; it[unidade] = "kg" }
            FichasTecnicasTable.insert {
                it[produtoId] = 1L; it[ingredienteId] = 10L
                it[quantidadeUsada] = java.math.BigDecimal("0.03"); it[unidade] = "kg"
            }
        }
        application { modulo(f) }
        val resposta = client.get("/promocoes/sugestoes") { autenticado() }
        assertEquals(HttpStatusCode.OK, resposta.status)
        val s = json.decodeFromString<List<SugestaoPromocaoDto>>(resposta.bodyAsText()).single { it.loteId == 1L && it.pratoId == 1L }
        assertEquals(setOf("MARGEM", "MELHOR_DIA"), s.avisos.map { it.campo }.toSet())
        assertEquals("Alface", s.avisos.single { it.campo == "MARGEM" }.ingredienteNome)
        assertNull(s.margemPct)
        s.avisos.forEach { assertTrue(s.observacao!!.contains(it.mensagem)) }
        transaction(f.banco) { assertEquals(0L, PromocoesTable.selectAll().count()) }
    }

    @Test
    fun `todas as rotas exigem JWT inclusive PATCHs antigos`() = testApplication {
        application { modulo(PromocaoFixture()) }
        for (path in listOf("/promocoes", "/promocoes/resumo", "/promocoes/sugestoes", "/promocoes/pendentes")) {
            assertEquals(HttpStatusCode.Unauthorized, client.get(path).status)
        }
        assertEquals(HttpStatusCode.Unauthorized, client.post("/promocoes").status)
        for (acao in listOf("ativar", "recusar", "cancelar", "confirmar")) {
            assertEquals(HttpStatusCode.Unauthorized, client.patch("/promocoes/1/$acao").status)
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/promocoes") { bearerAuth("invalido") }.status)
    }

    @Test
    fun `cancelar devolve lote a pendentes e permite novo desconto`() = testApplication {
        val f = PromocaoFixture()
        application { modulo(f) }
        val criada = client.post("/promocoes") { autenticado("""{"loteId":1,"pratoId":1}""") }
        assertEquals(HttpStatusCode.OK, criada.status)
        val primeira = json.decodeFromString<PromocaoDto>(criada.bodyAsText())
        assertEquals("Tomate", primeira.ingredienteNome)
        assertEquals("4021", primeira.numeroLote)
        assertEquals(f.hoje.plusDays(7).toString(), primeira.prazoLimiteVenda)
        assertEquals("CRITICO", primeira.criticidade)
        val periodo = """{"descontoPct":20,"dataInicio":"${f.hoje}","dataFim":"${f.hoje}"}"""
        assertEquals(HttpStatusCode.OK, client.patch("/promocoes/${primeira.id}/ativar") { autenticado(periodo) }.status)
        val cancelada = client.patch("/promocoes/${primeira.id}/cancelar") { autenticado() }
        assertEquals(HttpStatusCode.OK, cancelada.status)
        val dto = json.decodeFromString<PromocaoDto>(cancelada.bodyAsText())
        assertEquals(StatusPromocao.SUGERIDA, dto.status)
        assertNull(dto.descontoPct)
        assertNull(dto.dataInicio)
        assertEquals(HttpStatusCode.OK, client.patch("/promocoes/${primeira.id}/cancelar") { autenticado() }.status)
        val pendentes = client.get("/promocoes/pendentes") { autenticado() }
        assertTrue(pendentes.bodyAsText().contains("\"loteId\":1"))
        val novoPeriodo = """{"descontoPct":31,"dataInicio":"${f.hoje}","dataFim":"${f.hoje}"}"""
        val reativada = client.patch("/promocoes/${primeira.id}/ativar") { autenticado(novoPeriodo) }
        assertEquals(HttpStatusCode.OK, reativada.status)
        assertEquals(31, json.decodeFromString<PromocaoDto>(reativada.bodyAsText()).descontoPct)
        assertEquals(HttpStatusCode.OK, client.patch("/promocoes/${primeira.id}/cancelar") { autenticado() }.status)
        assertEquals(HttpStatusCode.OK, client.patch("/promocoes/pendentes/1/descartar") { autenticado() }.status)
        assertFalse(client.get("/promocoes/pendentes") { autenticado() }.bodyAsText().contains("\"loteId\":1"))
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
