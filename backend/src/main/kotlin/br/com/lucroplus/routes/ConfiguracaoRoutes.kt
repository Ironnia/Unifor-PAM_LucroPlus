package br.com.lucroplus.routes

import br.com.lucroplus.security.restauranteAdministrado
import br.com.lucroplus.security.exigirAdmin
import br.com.lucroplus.database.RestaurantesTable
import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.models.RestauranteDto
import org.jetbrains.exposed.sql.selectAll
import br.com.lucroplus.models.ErrorResponse
import br.com.lucroplus.models.MessageResponse
import br.com.lucroplus.models.PdvConfigDto
import br.com.lucroplus.services.ConfiguracaoService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.configuracaoRoutes() {

    authenticate("auth-jwt") {
        route("/configuracoes") {
            get("/restaurantes") {
                if (!call.exigirAdmin()) return@get
                call.respond(dbQuery {
                    RestaurantesTable.selectAll().map { RestauranteDto(it[RestaurantesTable.id], it[RestaurantesTable.nome]) }
                })
            }
            get("/pdv") {
                val restauranteId = call.restauranteAdministrado() ?: return@get
                val config = ConfiguracaoService(restauranteId).obterPdvConfig()
                call.respond(HttpStatusCode.OK, config)
            }

            post("/pdv") {
                val restauranteId = call.restauranteAdministrado() ?: return@post
                val config = try {
                    call.receive<PdvConfigDto>()
                } catch (_: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Parâmetros de configuração inválidos"))
                    return@post
                }

                val erroValidacao = ConfiguracaoService(restauranteId).validarPdvConfig(config)
                if (erroValidacao != null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(erroValidacao))
                    return@post
                }

                ConfiguracaoService(restauranteId).salvarPdvConfig(config)
                call.respond(HttpStatusCode.OK, MessageResponse("Configurações salvas com sucesso!"))
            }

            post("/pdv/testar-conexao") {
                val restauranteId = call.restauranteAdministrado() ?: return@post
                val config = try {
                    call.receive<PdvConfigDto>()
                } catch (_: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Parâmetros de teste inválidos"))
                    return@post
                }

                val salva = ConfiguracaoService(restauranteId).obterPdvConfig(incluirSenha = true)
                val resultado = ConfiguracaoService(restauranteId).testarConexao(
                    config.copy(password = config.password ?: config.senha ?: salva.password)
                )

                val status = if (resultado.sucesso) HttpStatusCode.OK else HttpStatusCode.BadRequest
                call.respond(status, resultado)
            }
        }
    }
}
