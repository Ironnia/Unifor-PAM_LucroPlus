package br.com.lucroplus.routes

import br.com.lucroplus.models.*
import br.com.lucroplus.services.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// rotas autenticadas de promocoes
fun Route.promocaoRoutes(
    operacoes: AlertaOperacoes = AlertaService,
    ciclo: PromocaoOperacoes = CicloPromocaoService()
) {
    authenticate("auth-jwt") {
        route("/promocoes") {
            get("/pendentes") { call.respond(operacoes.listarLotesPendentes()) }
            get("/previas") { call.respond(MotorPromocaoService.listarPrevias()) }
            get("/sugestoes") { call.respond(MotorPromocaoService.listarSugestoes()) }
            get {
                call.responderPromocao {
                    val filtro = call.request.queryParameters["status"]
                    val status = filtro?.let { valor ->
                        StatusPromocao.entries.find { it.name == valor }
                            ?: throw ErroPromocao(400, "Status inválido")
                    }
                    call.respond(ciclo.listar(status))
                }
            }
            get("/resumo") { call.responderPromocao { call.respond(ciclo.resumo()) } }
            patch("/pendentes/{loteId}/descartar") {
                call.responderPromocao {
                    val loteId = call.parameters["loteId"]?.toLongOrNull()?.takeIf { it > 0 }
                        ?: throw ErroPromocao(400, "ID de lote inválido")
                    ciclo.descartarPendente(loteId)
                    call.respond(MessageResponse("Pendência descartada"))
                }
            }
            post {
                call.responderPromocao {
                    call.respond(HttpStatusCode.OK, ciclo.criar(call.receive<CriarPromocaoRequest>()))
                }
            }
            patch("/{id}/ativar") {
                call.responderPromocao {
                    call.respond(ciclo.ativar(call.promocaoId(), call.receive<AtivarPromocaoRequest>()))
                }
            }
            patch("/{id}/recusar") {
                call.responderPromocao { call.respond(ciclo.recusar(call.promocaoId())) }
            }
            patch("/{id}/confirmar") {
                call.responderPromocao {
                    call.respond(ciclo.confirmar(call.promocaoId(), call.receive<ConfirmarPromocaoRequest>()))
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
