package br.com.lucroplus.routes

import br.com.lucroplus.security.restauranteAutenticado
import br.com.lucroplus.services.RelatorioService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.relatorioRoutes() {
    authenticate("auth-jwt") {
        route("/relatorios") {

            get("/desperdicio") {
                val restauranteId = call.restauranteAutenticado() ?: return@get
                val desperdicio = RelatorioService(restauranteId).obterDesperdicioMesAtual()
                call.respond(HttpStatusCode.OK, desperdicio)
            }

            get("/historico") {
                val restauranteId = call.restauranteAutenticado() ?: return@get
                val historico = RelatorioService(restauranteId).obterHistoricoDesperdicio()
                call.respond(HttpStatusCode.OK, historico)
            }
        }
    }
}
