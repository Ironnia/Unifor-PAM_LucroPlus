package br.com.lucroplus.routes

import br.com.lucroplus.security.restauranteAutenticado
import br.com.lucroplus.services.LoteService
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
        }
    }
}
