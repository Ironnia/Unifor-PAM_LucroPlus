package br.com.lucroplus.security

import br.com.lucroplus.database.*
import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.models.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.jetbrains.exposed.sql.selectAll

data class ContaAutenticada(val id: Long, val tipo: String, val restauranteId: Long?)

suspend fun ApplicationCall.contaAutenticada(): ContaAutenticada? {
    val payload = principal<JWTPrincipal>()?.payload
    val id = runCatching { payload?.getClaim("id")?.asLong() }.getOrNull()
    val restauranteDoToken = runCatching { payload?.getClaim("restaurante_id")?.asLong() }.getOrNull()
    val conta = id?.let {
        dbQuery {
            UsuariosTable.selectAll().where { UsuariosTable.id eq id }.singleOrNull()
                ?.takeIf { it[UsuariosTable.ativo] }
                ?.let { ContaAutenticada(it[UsuariosTable.id], it[UsuariosTable.tipo], it[UsuariosTable.restauranteId]) }
        }
    }
    if (conta == null) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Conta indisponível"))
        return null
    }
    // Tokens antigos, sem a claim, continuam válidos. Quando a claim existe, ela
    if (restauranteDoToken != conta.restauranteId) {
        respond(HttpStatusCode.Forbidden, ErrorResponse("Sessão vinculada a outro restaurante"))
        return null
    }
    return conta
}

suspend fun ApplicationCall.restauranteAutenticado(): Long? {
    val conta = contaAutenticada() ?: return null
    if (conta.restauranteId == null) {
        respond(HttpStatusCode.Forbidden, ErrorResponse("Conta sem restaurante vinculado. Contate o administrador."))
        return null
    }

    // A loja do banco é a fonte de verdade. O cliente pode repetir esse ID na URL,
    val restauranteSolicitado = request.queryParameters["restauranteId"]
    if (restauranteSolicitado != null) {
        val idSolicitado = restauranteSolicitado.toLongOrNull()?.takeIf { it > 0 }
        if (idSolicitado == null) {
            respond(HttpStatusCode.BadRequest, ErrorResponse("Restaurante inválido"))
            return null
        }
        if (idSolicitado != conta.restauranteId) {
            respond(HttpStatusCode.Forbidden, ErrorResponse("Acesso a outro restaurante não permitido"))
            return null
        }
    }
    return conta.restauranteId
}

suspend fun ApplicationCall.exigirAdmin(): Boolean {
    val conta = contaAutenticada() ?: return false
    if (conta.tipo != "ADMIN") {
        respond(HttpStatusCode.Forbidden, ErrorResponse("Apenas ADMIN pode configurar ou testar o PDV"))
        return false
    }
    return true
}

suspend fun ApplicationCall.restauranteAdministrado(): Long? {
    if (!exigirAdmin()) return null
    val id = request.queryParameters["restauranteId"]?.toLongOrNull()?.takeIf { it > 0 }
    if (id == null) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("Selecione um restaurante"))
        return null
    }
    if (!dbQuery { RestaurantesTable.selectAll().where { RestaurantesTable.id eq id }.any() }) {
        respond(HttpStatusCode.NotFound, ErrorResponse("Restaurante não encontrado"))
        return null
    }
    return id
}