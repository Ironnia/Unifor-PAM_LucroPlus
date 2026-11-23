package br.com.lucroplus

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class HealthRouteTest {
    @Test
    fun `health responde sem token`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { healthRoute() }
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"UP\""))
    }

    @Test
    fun `porta respeita prioridade e intervalo valido`() {
        assertEquals(9000, serverPort("9000", "8081"))
        assertEquals(8081, serverPort(null, "8081"))
        assertEquals(8080, serverPort(null, null))
        assertEquals(8080, serverPort("0", "8081"))
        assertEquals(8080, serverPort("65536", null))
        assertEquals(8080, serverPort("invalida", null))
    }
}
