package br.com.lucroplus.routes

import br.com.lucroplus.database.*
import br.com.lucroplus.security.JwtConfig
import com.auth0.jwt.JWT
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
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import kotlin.test.*

class AuthRoutesTest {
    private fun banco(): Database {
        val db = Database.connect("jdbc:h2:mem:auth_${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver")
        TransactionManager.defaultDatabase = db
        transaction(db) {
            SchemaUtils.create(RestaurantesTable, UsuariosTable)
            for (i in 1L..2L) RestaurantesTable.insert { it[id] = i; it[nome] = "Loja $i" }
            for (i in 1L..5L) UsuariosTable.insert {
                it[id] = i; it[nome] = "Conta $i"; it[email] = "conta$i@e1a.example"
                it[senhaHash] = BCrypt.hashpw("teste123", BCrypt.gensalt(4))
                it[tipo] = if (i == 4L) "ADMIN" else "GERENTE"
                it[ativo] = i != 5L
                it[restauranteId] = if (i <= 2L) i else null
            }
        }
        return db
    }

    private fun Application.modulo() {
        install(ContentNegotiation) { json(Json { encodeDefaults = true; ignoreUnknownKeys = true }) }
        install(Authentication) {
            jwt("auth-jwt") {
                verifier(JwtConfig.verifier)
                validate { JWTPrincipal(it.payload) }
            }
        }
        routing { authRoutes() }
    }

    private fun token(id: Long) = JwtConfig.generateToken(id, "antigo@e1a.example", "GERENTE", "Nome antigo")

    @Test
    fun `me retorna loja de cada gerente e ignora loja enviada na URL`() = testApplication {
        banco(); application { modulo() }
        for (i in 1L..2L) {
            val res = client.get("/auth/me?restauranteId=999") { bearerAuth(token(i)) }
            assertEquals(HttpStatusCode.OK, res.status)
            val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
            assertEquals(i, body.getValue("restauranteId").jsonPrimitive.long)
            assertEquals("Loja $i", body.getValue("restauranteNome").jsonPrimitive.content)
            assertEquals("Conta $i", body.getValue("nome").jsonPrimitive.content)
        }
    }

    @Test
    fun `conta antiga e admin sem loja retornam null`() = testApplication {
        banco(); application { modulo() }
        for (i in 3L..4L) {
            val res = client.get("/auth/me") { bearerAuth(token(i)) }
            assertEquals(HttpStatusCode.OK, res.status)
            val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
            assertEquals(JsonNull, body["restauranteId"])
            assertEquals(JsonNull, body["restauranteNome"])
        }
    }

    @Test
    fun `me consulta vinculo atual mesmo com token antigo`() = testApplication {
        val db = banco(); application { modulo() }
        val acesso = token(1)
        transaction(db) { UsuariosTable.update({ UsuariosTable.id eq 1L }) { it[restauranteId] = 2L } }
        val res = client.get("/auth/me") { bearerAuth(acesso) }
        assertEquals(2L, Json.parseToJsonElement(res.bodyAsText()).jsonObject.getValue("restauranteId").jsonPrimitive.long)
    }

    @Test
    fun `sessao ausente invalida conta removida ou inativa e negada`() = testApplication {
        banco(); application { modulo() }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/auth/me").status)
        for (acesso in listOf("invalido", token(99), token(5))) {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/auth/me") { bearerAuth(acesso) }.status)
        }
    }

    @Test
    fun `login permanece funcional e senha errada e negada`() = testApplication {
        banco(); application { modulo() }
        val res = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"conta1@e1a.example","senha":"teste123"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        val acesso = body.getValue("token").jsonPrimitive.content
        assertEquals(1L, body.getValue("restauranteId").jsonPrimitive.long)
        assertEquals("Loja 1", body.getValue("restauranteNome").jsonPrimitive.content)
        assertEquals(1L, JWT.decode(acesso).getClaim("restaurante_id").asLong())
        assertEquals(HttpStatusCode.OK, client.get("/auth/me") { bearerAuth(acesso) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"conta1@e1a.example","senha":"errada"}""")
        }.status)
    }

    @Test
    fun `token antigo sem loja continua valido e claim divergente retorna 403`() = testApplication {
        banco(); application { modulo() }
        val tokenAntigo = JwtConfig.generateToken(1, "antigo@example.invalid", "GERENTE", "Antigo")
        val tokenComLojaAntiga = JwtConfig.generateToken(1, "antigo@example.invalid", "GERENTE", "Antigo", 2)

        val legado = client.get("/auth/me") { bearerAuth(tokenAntigo) }
        assertEquals(HttpStatusCode.OK, legado.status)
        assertEquals(1L, Json.parseToJsonElement(legado.bodyAsText()).jsonObject
            .getValue("restauranteId").jsonPrimitive.long)
        assertEquals(HttpStatusCode.Forbidden, client.get("/auth/me") { bearerAuth(tokenComLojaAntiga) }.status)
    }
}