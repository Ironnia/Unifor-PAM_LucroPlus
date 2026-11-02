package br.com.lucroplus.routes

import br.com.lucroplus.security.restauranteAutenticado
import br.com.lucroplus.services.LoteService
import br.com.lucroplus.models.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.loteRoutes() {
    route("/lotes") {
        authenticate("auth-jwt") {
            get {
                val restauranteId = call.restauranteAutenticado() ?: return@get
                val lotes = LoteService(restauranteId).listarLotes()
                call.respond(HttpStatusCode.OK, lotes)
            }
            get("/{ingredienteId}/historico") {
                val restauranteId = call.restauranteAutenticado() ?: return@get
                val ingredienteId = call.parameters["ingredienteId"]?.toLongOrNull()?.takeIf { it > 0 }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ID de ingrediente inválido"))
                val historico = LoteService(restauranteId).historicoCompras(ingredienteId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Ingrediente não encontrado"))
                call.respond(HttpStatusCode.OK, historico)
            }
        }
    }
}
