package dev.burufi.chatting.durable.server

import dev.burufi.chatting.durable.server.testing.serverTest
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthRouteTest {
    @Test
    fun `health answers with no database anywhere, which is what makes it a liveness probe`() =
        testApplication {
            application(Application::module)

            val response = client.get("/health")

            assertEquals(
                HttpStatusCode.OK,
                response.status,
                "compose restarts the server on a failing probe, so health must not fail with Postgres",
            )
            assertEquals("""{"status":"ok"}""", response.bodyAsText(), "the probe body is part of the contract too")
        }

    @Test
    fun `health negotiates JSON, so the plugin every later route needs is proven wired`() =
        testApplication {
            application(Application::module)

            assertEquals(
                ContentType.Application.Json,
                client.get("/health").contentType()?.withoutParameters(),
                "a plain-text body here would mean ContentNegotiation is absent and nobody noticed",
            )
        }

    @Test
    fun `the real module boots under the harness, the seam every later route test uses`() {
        serverTest(Application::module) {
            assertEquals(
                HttpStatusCode.OK,
                client.get("/health").status,
                "ServerFixture takes the module as a parameter for this; W-05 is what discharges it",
            )
        }
    }
}
