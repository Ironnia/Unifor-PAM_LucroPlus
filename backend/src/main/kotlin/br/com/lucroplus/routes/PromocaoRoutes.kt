package br.com.lucroplus.routes

import br.com.lucroplus.models.ErrorResponse
import br.com.lucroplus.models.MessageResponse
import br.com.lucroplus.services.AlertaOperacoes
import br.com.lucroplus.services.AlertaService
import br.com.lucroplus.services.MotorPromocaoService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.promocaoRoutes(operacoes: AlertaOperacoes = AlertaService) {
    route("/promocoes") {
        authenticate("auth-jwt") {
            get("/pendentes") { call.respond(HttpStatusCode.OK, operacoes.listarLotesPendentes()) }
            get("/previas") { call.respond(HttpStatusCode.OK, MotorPromocaoService.listarPrevias()) }
            get("/sugestoes") { call.respond(HttpStatusCode.OK, MotorPromocaoService.listarSugestoes()) }
        }

        patch("/{id}/ativar") {
            val idParam = call.parameters["id"]?.toLongOrNull()
            if (idParam == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("ID de promoção inválido"))
                return@patch
            }

            val sucesso = MotorPromocaoService.ativarPromocao(idParam)
            if (sucesso) {
                call.respond(HttpStatusCode.OK, MessageResponse("Promoção ativada com sucesso!"))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Promoção não encontrada"))
            }
        }

        patch("/{id}/recusar") {
            val idParam = call.parameters["id"]?.toLongOrNull()
            if (idParam == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("ID de promoção inválido"))
                return@patch
            }

            val sucesso = MotorPromocaoService.recusarPromocao(idParam)
            if (sucesso) {
                call.respond(HttpStatusCode.OK, MessageResponse("Promoção recusada."))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Promoção não encontrada"))
            }
        }
    }
}
