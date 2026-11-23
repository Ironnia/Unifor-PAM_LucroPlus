package br.com.lucroplus.routes

import br.com.lucroplus.security.restauranteAutenticado
import br.com.lucroplus.services.ProdutoService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.produtoRoutes() {
    authenticate("auth-jwt") {
        route("/produtos") {

            get {
                val restauranteId = call.restauranteAutenticado() ?: return@get
                val produtos = ProdutoService(restauranteId).listarProdutos()
                call.respond(HttpStatusCode.OK, produtos)
            }

            get("/rentabilidade") {
                val restauranteId = call.restauranteAutenticado() ?: return@get
                val rentabilidade = ProdutoService(restauranteId).obterRentabilidadeProdutos()
                call.respond(HttpStatusCode.OK, rentabilidade)
            }
        }
    }
}
