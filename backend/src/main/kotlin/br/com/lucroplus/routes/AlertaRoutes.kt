package br.com.lucroplus.routes

import br.com.lucroplus.models.ErrorResponse
import br.com.lucroplus.models.MessageResponse
import br.com.lucroplus.services.AcaoAlerta
import br.com.lucroplus.services.AlertaOperacoes
import br.com.lucroplus.services.AlertaService
import br.com.lucroplus.services.ResultadoAcaoAlerta
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.alertaRoutes(operacoes: AlertaOperacoes = AlertaService) {
    authenticate("auth-jwt") {
        route("/alertas") {
            get("/vencimento") {
                call.respond(HttpStatusCode.OK, operacoes.obterAlertasVencimento())
            }
            patch("/{id}/salvar-lote") {
                call.responderAcao(operacoes, AcaoAlerta.SALVAR_LOTE)
            }
            patch("/{id}/ciente") {
                call.responderAcao(operacoes, AcaoAlerta.CIENTE)
            }
            patch("/{id}/visualizar") {
                call.responderAcao(operacoes, AcaoAlerta.CIENTE)
            }
        }
    }
}

private suspend fun ApplicationCall.responderAcao(operacoes: AlertaOperacoes, acao: AcaoAlerta) {
    val id = parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
    if (id == null) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("ID de alerta inválido"))
        return
    }
    when (operacoes.executarAcao(id, acao)) {
        ResultadoAcaoAlerta.CONCLUIDA, ResultadoAcaoAlerta.JA_APLICADA ->
            respond(HttpStatusCode.OK, MessageResponse(
                if (acao == AcaoAlerta.SALVAR_LOTE) "Lote salvo na fila de promoções pendentes"
                else "Alerta marcado como ciente"
            ))
        ResultadoAcaoAlerta.CONFLITO ->
            respond(HttpStatusCode.Conflict, ErrorResponse("O alerta já foi resolvido ou o lote não está ativo"))
        ResultadoAcaoAlerta.NAO_ENCONTRADO ->
            respond(HttpStatusCode.NotFound, ErrorResponse("Alerta não encontrado"))
    }
}
