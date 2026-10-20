package br.com.lucroplus.routes

import br.com.lucroplus.security.restauranteAutenticado
import br.com.lucroplus.services.DashboardService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.dashboardRoutes() {
    authenticate("auth-jwt") {
        route("/dashboard") {

            get("/kpis") {
                val restauranteId = call.restauranteAutenticado() ?: return@get
                val kpis = DashboardService(restauranteId).obterKpis()
                call.respond(HttpStatusCode.OK, kpis)
            }

            get("/vendas") {
                val restauranteId = call.restauranteAutenticado() ?: return@get
                val dadosGraficos = DashboardService(restauranteId).obterDadosGraficos()
                call.respond(HttpStatusCode.OK, dadosGraficos)
            }

            get("/previsao") {
                val restauranteId = call.restauranteAutenticado() ?: return@get
                val previsao = DashboardService(restauranteId).obterPrevisaoDemanda()
                call.respond(HttpStatusCode.OK, previsao)
            }
        }
    }
}
