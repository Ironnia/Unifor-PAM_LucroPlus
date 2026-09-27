package br.com.lucroplus

import br.com.lucroplus.database.DatabaseFactory
import br.com.lucroplus.models.ErrorResponse
import br.com.lucroplus.models.HealthResponse
import br.com.lucroplus.routes.alertaRoutes
import br.com.lucroplus.routes.authRoutes
import br.com.lucroplus.routes.configuracaoRoutes
import br.com.lucroplus.routes.dashboardRoutes
import br.com.lucroplus.routes.importacaoRoutes
import br.com.lucroplus.routes.loteRoutes
import br.com.lucroplus.routes.produtoRoutes
import br.com.lucroplus.routes.promocaoRoutes
import br.com.lucroplus.routes.relatorioRoutes
import br.com.lucroplus.security.JwtConfig
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main() {
    val serverHost = System.getenv("SERVER_HOST")?.trim()?.takeIf { it.isNotEmpty() } ?: "0.0.0.0"
    val serverPort = System.getenv("SERVER_PORT")
        ?.toIntOrNull()
        ?.takeIf { it in 1..65535 }
        ?: 8080

    embeddedServer(Netty, port = serverPort, host = serverHost, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()

    install(CallLogging) {
        level = Level.INFO
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = JwtConfig.REALM
            verifier(JwtConfig.verifier)
            validate { credential ->
                if (credential.payload.audience.contains(JwtConfig.AUDIENCE)) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("Token de autenticação inválido ou expirado")
                )
            }
        }
    }

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(erro = "Erro interno no servidor", detalhe = cause.localizedMessage)
            )
        }
    }

    routing {
        get("/") {
            call.respond(HealthResponse(
                status = "ok",
                mensagem = "LucroPlus Backend Ktor rodando com sucesso!"
            ))
        }

        authRoutes()
        produtoRoutes()
        alertaRoutes()
        promocaoRoutes()
        dashboardRoutes()
        relatorioRoutes()
        configuracaoRoutes()
        importacaoRoutes()
        loteRoutes()
    }
}
