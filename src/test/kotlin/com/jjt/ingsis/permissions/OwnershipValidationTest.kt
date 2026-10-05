package com.jjt.ingsis.permissions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.http.HttpResponse
import java.util.UUID

class OwnershipValidationTest
    @Autowired
    constructor(
        @LocalServerPort port: Int,
    ) : PostgresHttpTest(port) {
        @ParameterizedTest
        @ValueSource(strings = ["""{"ownerId":123}""", """{"ownerId":true}""", """{"ownerId":[]}"""])
        fun invalidOwnerTypeIsRejected(body: String) {
            assertInvalid(put(UUID.randomUUID().toString(), body))
        }

        @ParameterizedTest
        @ValueSource(strings = ["{}", """{"ownerId":null}""", """{"ownerId":""}""", """{"ownerId":" \t\n"}""", "{", ""])
        fun invalidBodyIsAProblemDetailsBadRequest(body: String) {
            assertInvalid(put(UUID.randomUUID().toString(), body))
        }

        @Test
        fun malformedIdsAndMissingOrBlankActorsAreRejected() {
            assertInvalid(put("not-a-uuid", """{"ownerId":"dev-thiago"}"""))
            assertInvalid(get("/ownership/not-a-uuid"))
            assertInvalid(get("/ownership/1-1-1-1-1"))
            assertInvalid(get("/ownership/not-a-uuid/can-modify?actorId=dev-thiago"))
            val path = "/ownership/${UUID.randomUUID()}/can-modify"
            assertInvalid(get(path))
            assertInvalid(get("$path?actorId="))
            assertInvalid(get("$path?actorId=%20%20"))
        }

        @Test
        fun unsupportedContentTypeProducesProblemDetails() {
            val response =
                send(
                    method = "PUT",
                    path = "/ownership/${UUID.randomUUID()}",
                    body = "owner=thiago",
                    contentType = "text/plain",
                )
            assertEquals(415, response.statusCode())
            assertTrue(
                response
                    .headers()
                    .firstValue("Content-Type")
                    .orElseThrow()
                    .startsWith("application/problem+json"),
            )
        }

        private fun assertInvalid(response: HttpResponse<String>) {
            assertEquals(400, response.statusCode())
            assertTrue(
                response
                    .headers()
                    .firstValue("Content-Type")
                    .orElseThrow()
                    .startsWith("application/problem+json"),
            )
            assertEquals("urn:permissions:problem:invalid-request", json(response)["type"].asString())
            assertEquals(400, json(response)["status"].asInt())
            assertTrue(json(response)["instance"].asString().startsWith("/ownership/"))
        }
    }
