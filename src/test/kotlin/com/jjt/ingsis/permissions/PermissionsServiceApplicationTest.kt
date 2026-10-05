package com.jjt.ingsis.permissions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import tools.jackson.databind.json.JsonMapper

class PermissionsServiceApplicationTest
    @Autowired
    constructor(
        @LocalServerPort port: Int,
    ) : PostgresHttpTest(port) {
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
    }
