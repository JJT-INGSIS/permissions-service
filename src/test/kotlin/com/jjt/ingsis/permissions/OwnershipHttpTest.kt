package com.jjt.ingsis.permissions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.http.HttpResponse
import java.util.UUID

class OwnershipHttpTest
    @Autowired
    constructor(
        @LocalServerPort port: Int,
    ) : PostgresHttpTest(port) {
        @Test
        fun registrationIsIdempotentAndPreservesOwnerOnConflict() {
            val id = UUID.randomUUID().toString()
            val created = put(id, """{"ownerId":"dev-thiago"}""")
            assertEquals(201, created.statusCode())
            assertEquals("/ownership/$id", created.headers().firstValue("Location").orElseThrow())
            assertEquals(id, json(created)["snippetId"].asString())
            assertEquals("dev-thiago", json(created)["ownerId"].asString())
            val repeated = put(id, """{"ownerId":"dev-thiago"}""")
            assertEquals(200, repeated.statusCode())
            assertEquals(json(created), json(repeated))
            val conflict = put(id, """{"ownerId":"someone-else"}""")
            assertProblem(response = conflict, status = 409, category = "owner-conflict", instance = "/ownership/$id")
            val owner = get("/ownership/$id")
            assertEquals(200, owner.statusCode())
            assertEquals(json(created), json(owner))
        }

        @Test
        fun permissionDistinguishesOwnerFromAnotherActor() {
            val id = UUID.randomUUID().toString()
            assertEquals(201, put(id, """{"ownerId":"dev-thiago"}""").statusCode())
            val allowed = get("/ownership/$id/can-modify?actorId=dev-thiago")
            val denied = get("/ownership/$id/can-modify?actorId=someone-else")
            assertEquals(200, allowed.statusCode())
            assertEquals(200, denied.statusCode())
            assertTrue(json(allowed)["allowed"].asBoolean())
            assertFalse(json(denied)["allowed"].asBoolean())
        }

        @Test
        fun unknownOwnershipIsDifferentFromDeniedPermission() {
            val path = "/ownership/${UUID.randomUUID()}"
            assertProblem(response = get(path), status = 404, category = "ownership-not-found", instance = path)
            assertProblem(
                response = get("$path/can-modify?actorId=dev-thiago"),
                status = 404,
                category = "ownership-not-found",
                instance = "$path/can-modify",
            )
        }

        @Test
        fun opaqueIdentityIsNotTrimmedOrNormalized() {
            val id = UUID.randomUUID().toString()
            val result = put(id, """{"ownerId":" auth0|abc123 "}""")
            assertEquals(201, result.statusCode())
            assertEquals(" auth0|abc123 ", json(result)["ownerId"].asString())
            assertFalse(json(get("/ownership/$id/can-modify?actorId=auth0%7Cabc123"))["allowed"].asBoolean())
            assertTrue(json(get("/ownership/$id/can-modify?actorId=%20auth0%7Cabc123%20"))["allowed"].asBoolean())
        }

        private fun assertProblem(
            response: HttpResponse<String>,
            status: Int,
            category: String,
            instance: String,
        ) {
            assertEquals(status, response.statusCode())
            assertTrue(
                response
                    .headers()
                    .firstValue("Content-Type")
                    .orElseThrow()
                    .startsWith("application/problem+json"),
            )
            val problem = json(response)
            assertEquals(status, problem["status"].asInt())
            assertEquals("urn:permissions:problem:$category", problem["type"].asString())
            assertEquals(instance, problem["instance"].asString())
            assertTrue(problem["title"].asString().isNotBlank())
            assertTrue(problem["detail"].asString().isNotBlank())
        }
    }
