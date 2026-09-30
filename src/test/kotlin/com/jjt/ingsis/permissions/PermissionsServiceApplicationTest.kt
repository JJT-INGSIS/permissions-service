package com.jjt.ingsis.permissions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PermissionsServiceApplicationTest
    @Autowired
    constructor(
        @LocalServerPort private val port: Int,
    ) {
        @Test
        @Suppress("EmptyFunctionBlock")
        fun contextLoads() {
        }

        @Test
        fun healthReportsUpWithoutDatabaseDetails() {
            val response = get("/actuator/health")
            val health = JsonMapper.builder().build().readTree(response.body())

            assertEquals(200, response.statusCode())
            assertEquals("UP", health["status"].asString())
            assertFalse(health.has("components"))
            assertFalse(health.has("details"))
        }

        @Test
        fun otherActuatorEndpointsAreNotExposed() {
            assertEquals(404, get("/actuator/env").statusCode())
        }

        private fun get(path: String): HttpResponse<String> =
            HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
    }
