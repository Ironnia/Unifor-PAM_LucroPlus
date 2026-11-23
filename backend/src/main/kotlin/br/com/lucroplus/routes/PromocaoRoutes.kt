package br.com.lucroplus.routes

import br.com.lucroplus.security.restauranteAutenticado
import br.com.lucroplus.models.*
import br.com.lucroplus.services.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.promocaoRoutes(
    operacoes: AlertaOperacoes? = null,
    ciclo: PromocaoOperacoes? = null
) {
    authenticate("auth-jwt") {
        route("/promocoes") {
            get("/pendentes") {
                val restauranteId = call.restauranteAutenticado() ?: return@get
                call.respond((operacoes ?: AlertaService(restauranteId)).listarLotesPendentes()) }
            get("/sugestoes") {
                val restauranteId = call.restauranteAutenticado() ?: return@get
                call.respond(MotorPromocaoService(restauranteId).listarSugestoes()) }
            get {
                val restauranteId = call.restauranteAutenticado() ?: return@get
                call.responderPromocao {
                    val filtro = call.request.queryParameters["status"]
                    val status = filtro?.let { valor ->
                        StatusPromocao.entries.find { it.name == valor }
                            ?: throw ErroPromocao(400, "Status inválido")
                    }
                    call.respond((ciclo ?: CicloPromocaoService(restauranteId)).listar(status))
                }
            }
            get("/resumo") {
                val restauranteId = call.restauranteAutenticado() ?: return@get
                call.responderPromocao { call.respond((ciclo ?: CicloPromocaoService(restauranteId)).resumo()) } }
            patch("/pendentes/{loteId}/descartar") {
                val restauranteId = call.restauranteAutenticado() ?: return@patch
                call.responderPromocao {
                    val loteId = call.parameters["loteId"]?.toLongOrNull()?.takeIf { it > 0 }
                        ?: throw ErroPromocao(400, "ID de lote inválido")
                    (ciclo ?: CicloPromocaoService(restauranteId)).descartarPendente(loteId)
                    call.respond(MessageResponse("Pendência descartada"))
                }
            }
            post {
                val restauranteId = call.restauranteAutenticado() ?: return@post
                call.responderPromocao {
                    call.respond(HttpStatusCode.OK, (ciclo ?: CicloPromocaoService(restauranteId)).criar(call.receive<CriarPromocaoRequest>()))
                }
            }
            patch("/{id}/ativar") {
                val restauranteId = call.restauranteAutenticado() ?: return@patch
                call.responderPromocao {
                    call.respond((ciclo ?: CicloPromocaoService(restauranteId)).ativar(call.promocaoId(), call.receive<AtivarPromocaoRequest>()))
                }
            }
            patch("/{id}/recusar") {
                val restauranteId = call.restauranteAutenticado() ?: return@patch
                call.responderPromocao { call.respond((ciclo ?: CicloPromocaoService(restauranteId)).recusar(call.promocaoId())) }
            }
            patch("/{id}/cancelar") {
                val restauranteId = call.restauranteAutenticado() ?: return@patch
                call.responderPromocao { call.respond((ciclo ?: CicloPromocaoService(restauranteId)).cancelar(call.promocaoId())) }
            }
            patch("/{id}/confirmar") {
                val restauranteId = call.restauranteAutenticado() ?: return@patch
                call.responderPromocao {
                    call.respond((ciclo ?: CicloPromocaoService(restauranteId)).confirmar(call.promocaoId(), call.receive<ConfirmarPromocaoRequest>()))
                }
            }
        }
    }
}

private fun ApplicationCall.promocaoId(): Long = parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
    ?: throw ErroPromocao(400, "ID de promoção inválido")

private suspend fun ApplicationCall.responderPromocao(bloco: suspend () -> Unit) {
    try { bloco() }
    catch (erro: ErroPromocao) { respond(HttpStatusCode.fromValue(erro.codigo), ErrorResponse(erro.message)) }
    catch (_: BadRequestException) { respond(HttpStatusCode.BadRequest, ErrorResponse("Corpo JSON inválido ou incompleto")) }
}
