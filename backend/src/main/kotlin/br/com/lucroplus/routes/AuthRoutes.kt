package br.com.lucroplus.routes

import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.database.UsuariosTable
import br.com.lucroplus.models.ErrorResponse
import br.com.lucroplus.models.LoginRequest
import br.com.lucroplus.models.LoginResponse
import br.com.lucroplus.models.RegisterRequest
import br.com.lucroplus.security.JwtConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.mindrot.jbcrypt.BCrypt

fun Route.authRoutes() {
    route("/auth") {

        post("/login") {
            val request = try {
                call.receive<LoginRequest>()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Formato de requisição inválido"))
                return@post
            }

            if (request.email.isBlank() || request.senha.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Email e senha são obrigatórios"))
                return@post
            }

            val usuario = dbQuery {
                UsuariosTable
                    .selectAll()
                    .where { UsuariosTable.email eq request.email.trim() }
                    .singleOrNull()
            }

            if (usuario == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Email ou senha inválidos"))
                return@post
            }

            val ativo = usuario[UsuariosTable.ativo]
            if (!ativo) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("Usuário inativo. Contate o administrador."))
                return@post
            }

            val hashNoBanco = usuario[UsuariosTable.senhaHash]
            val senhaValida = hashNoBanco == request.senha || try {
                BCrypt.checkpw(request.senha, hashNoBanco)
            } catch (e: Exception) {
                false
            }

            if (!senhaValida) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Email ou senha inválidos"))
                return@post
            }

            val id = usuario[UsuariosTable.id]
            val nome = usuario[UsuariosTable.nome]
            val email = usuario[UsuariosTable.email]
            val tipo = usuario[UsuariosTable.tipo]

            val token = JwtConfig.generateToken(id, email, tipo, nome)

            call.respond(
                HttpStatusCode.OK,
                LoginResponse(
                    token = token,
                    expiresIn = 28800L,
                    id = id,
                    nome = nome,
                    email = email,
                    tipo = tipo
                )
            )
        }

        post("/register") {
            val request = try {
                call.receive<RegisterRequest>()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Formato de requisição inválido"))
                return@post
            }

            if (request.nome.isBlank() || request.email.isBlank() || request.senha.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Todos os campos são obrigatórios"))
                return@post
            }

            if (!request.email.contains("@") || !request.email.contains(".")) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Email em formato inválido"))
                return@post
            }

            if (request.senha.length < 6) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("A senha deve ter no mínimo 6 caracteres"))
                return@post
            }

            val emailExiste = dbQuery {
                UsuariosTable
                    .selectAll()
                    .where { UsuariosTable.email eq request.email.trim() }
                    .count() > 0
            }

            if (emailExiste) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("Email já cadastrado no sistema"))
                return@post
            }

            val salt = BCrypt.gensalt(10)
            val senhaHash = BCrypt.hashpw(request.senha, salt)

            val novoId = dbQuery {
                UsuariosTable.insert {
                    it[nome] = request.nome.trim()
                    it[email] = request.email.trim().lowercase()
                    it[this.senhaHash] = senhaHash
                    it[tipo] = request.tipo.trim().uppercase()
                    it[ativo] = true
                } get UsuariosTable.id
            }

            val token = JwtConfig.generateToken(novoId, request.email.trim().lowercase(), request.tipo.trim().uppercase(), request.nome.trim())

            call.respond(
                HttpStatusCode.Created,
                LoginResponse(
                    token = token,
                    expiresIn = 28800L,
                    id = novoId,
                    nome = request.nome.trim(),
                    email = request.email.trim().lowercase(),
                    tipo = request.tipo.trim().uppercase()
                )
            )
        }

        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Token inválido ou não informado"))
                    return@get
                }

                val id = principal.payload.getClaim("id").asLong() ?: 0L
                val nome = principal.payload.getClaim("nome").asString() ?: "Usuário"
                val email = principal.payload.getClaim("email").asString() ?: ""
                val tipo = principal.payload.getClaim("tipo").asString() ?: "GERENTE"

                call.respond(
                    HttpStatusCode.OK,
                    LoginResponse(
                        token = null,
                        expiresIn = null,
                        id = id,
                        nome = nome,
                        email = email,
                        tipo = tipo
                    )
                )
            }
        }
    }
}