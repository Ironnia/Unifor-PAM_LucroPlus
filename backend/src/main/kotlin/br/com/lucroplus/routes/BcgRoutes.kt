package br.com.lucroplus.routes

import br.com.lucroplus.models.BcgFechamentoRequest
import br.com.lucroplus.models.ErrorResponse
import br.com.lucroplus.security.restauranteAutenticado
import br.com.lucroplus.services.BcgOperacoes
import br.com.lucroplus.services.BcgService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.bcgRoutes(criarServico: (Long) -> BcgOperacoes = { BcgService(it) }) {
    authenticate("auth-jwt") {
        route("/bcg") {
            get {
                val restauranteId = call.restauranteAutenticado() ?: return@get
                call.respond(HttpStatusCode.OK, criarServico(restauranteId).listar())
            }
            post("/fechar-mes") {
                val restauranteId = call.restauranteAutenticado() ?: return@post
                val requisicao = call.receive<BcgFechamentoRequest>()
                val resposta = runCatching {
                    criarServico(restauranteId).fecharMes(requisicao.mesReferencia)
                }.getOrElse { erro ->
                    if (erro is IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(erro.message ?: "Mês inválido"))
                        return@post
                    }
                    throw erro
                }
                call.respond(HttpStatusCode.OK, resposta)
            }
        }
    }
}
