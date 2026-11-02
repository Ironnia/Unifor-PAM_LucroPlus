package br.com.lucroplus.routes

import br.com.lucroplus.security.restauranteAutenticado
import br.com.lucroplus.services.PrevisaoComprasService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.estoquePrevisaoRoutes() {
    authenticate("auth-jwt") {
        route("/estoque") {
            get("/previsao-compras") {
                val restauranteId = call.restauranteAutenticado() ?: return@get
                call.respond(HttpStatusCode.OK, PrevisaoComprasService(restauranteId).obter())
            }
        }
    }
}
