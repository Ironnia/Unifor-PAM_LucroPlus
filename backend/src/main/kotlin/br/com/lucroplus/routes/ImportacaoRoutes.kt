package br.com.lucroplus.routes

import br.com.lucroplus.security.restauranteAutenticado
import br.com.lucroplus.models.ErrorResponse
import br.com.lucroplus.models.ImportacaoResponse
import br.com.lucroplus.services.ImportacaoService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.importacaoRoutes() {
    authenticate("auth-jwt") {
        route("/importacao") {

            post("/produtos") {
                val restauranteId = call.restauranteAutenticado() ?: return@post
                val csvText = call.extrairConteudoCsv()
                if (csvText == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Nenhum arquivo CSV foi enviado"))
                    return@post
                }

                val resultado = ImportacaoService(restauranteId).importarProdutos(csvText)
                call.respond(HttpStatusCode.OK, resultado)
            }

            post("/ingredientes") {
                val restauranteId = call.restauranteAutenticado() ?: return@post
                val csvText = call.extrairConteudoCsv()
                if (csvText == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Nenhum arquivo CSV foi enviado"))
                    return@post
                }

                val resultado = ImportacaoService(restauranteId).importarIngredientes(csvText)
                call.respond(HttpStatusCode.OK, resultado)
            }

            post("/ficha-tecnica") {
                val restauranteId = call.restauranteAutenticado() ?: return@post
                val csvText = call.extrairConteudoCsv()
                if (csvText == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Nenhum arquivo CSV foi enviado"))
                    return@post
                }

                val resultado = ImportacaoService(restauranteId).importarFichaTecnica(csvText)
                call.respond(HttpStatusCode.OK, resultado)
            }

            post("/vendas") {
                val restauranteId = call.restauranteAutenticado() ?: return@post
                val csvText = call.extrairConteudoCsv()
                if (csvText == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Nenhum arquivo CSV foi enviado"))
                    return@post
                }

                val resultado = ImportacaoService(restauranteId).importarVendas(csvText)
                call.respond(HttpStatusCode.OK, resultado)
            }

            post("/pdv") {
                val restauranteId = call.restauranteAutenticado() ?: return@post
                val resultado = ImportacaoService(restauranteId).sincronizarPdv()
                call.respond(HttpStatusCode.OK, resultado)
            }

            get("/pdv/status") {
                val restauranteId = call.restauranteAutenticado() ?: return@get
                call.respond(ImportacaoService(restauranteId).statusPdv())
            }
        }
    }
}

private suspend fun ApplicationCall.extrairConteudoCsv(): String? {
    val contentType = request.contentType()
    return if (contentType.match(ContentType.MultiPart.FormData)) {
        try {
            val multipart = receiveMultipart()
            var conteudo: String? = null
            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    conteudo = part.streamProvider().readBytes().toString(Charsets.UTF_8)
                } else if (part is PartData.FormItem) {
                    conteudo = part.value
                }
                part.dispose()
            }
            conteudo
        } catch (e: Exception) {
            null
        }
    } else {
        try {
            val text = receiveText()
            if (text.isNotBlank()) text else null
        } catch (e: Exception) {
            null
        }
    }
}
