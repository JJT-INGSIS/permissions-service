package com.jjt.ingsis.permissions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.http.HttpResponse
import java.util.UUID

class AccessHttpTest
    @Autowired
    constructor(
        @LocalServerPort port: Int,
    ) : PostgresHttpTest(port) {
        @Test
        fun recipientCanReadButNotModify() {
            val (owner, recipient) = actors(2)
            val id = ownedBy(owner)
            val created = share(id, recipient, owner)
            assertEquals(201, created.statusCode())
            assertEquals(id, json(created)["snippetId"].asString())
            assertEquals(recipient, json(created)["recipientId"].asString())
            assertEquals("shared", relation(id, recipient))
            assertEquals("owned", relation(id, owner))
            assertFalse(json(get("/ownership/$id/can-modify?actorId=$recipient"))["allowed"].asBoolean())
            assertTrue(json(get("/ownership/$id/can-modify?actorId=$owner"))["allowed"].asBoolean())
            assertEquals(owner, json(get("/ownership/$id"))["ownerId"].asString())
        }

        @Test
        fun repeatingAShareReturnsTheSameResultWithoutDuplicates() {
            val (owner, recipient) = actors(2)
            val id = ownedBy(owner)
            val created = share(id, recipient, owner)
            val repeated = share(id, recipient, owner)
            assertEquals(201, created.statusCode())
            assertEquals(200, repeated.statusCode())
            assertEquals(json(created), json(repeated))
            assertEquals(listOf(id), ids(recipient, "shared"))
        }

        @Test
        fun onlyTheOwnerCanShare() {
            val (owner, recipient, stranger) = actors(3)
            val id = ownedBy(owner)
            share(id, recipient, owner)
            assertDenied(share(id, stranger, recipient), "shared")
            assertDenied(share(id, recipient, stranger), "none")
            assertEquals("none", relation(id, stranger))
        }

        @Test
        fun nobodySharesWithThemselves() {
            val (owner, recipient, stranger) = actors(3)
            val id = ownedBy(owner)
            share(id, recipient, owner)
            val ownProblem = share(id, owner, owner)
            assertEquals(422, ownProblem.statusCode())
            assertEquals("urn:permissions:problem:invalid-recipient", json(ownProblem)["type"].asString())
            assertDenied(share(id, recipient, recipient), "shared")
            assertDenied(share(id, stranger, stranger), "none")
            assertEquals("none", relation(id, stranger))
        }

        @Test
        fun unknownOwnershipIsNotFoundForAccessAndSharing() {
            val id = UUID.randomUUID()
            assertEquals(404, get("/ownership/$id/access?actorId=dev-juan").statusCode())
            val shared = share(id.toString(), "dev-thiago", "dev-juan")
            assertEquals(404, shared.statusCode())
            assertEquals("urn:permissions:problem:ownership-not-found", json(shared)["type"].asString())
        }

        @Test
        fun listingSeparatesOwnedAndSharedWithoutDuplicates() {
            val (actor, other) = actors(2)
            val owned = List(2) { ownedBy(actor) }
            val shared = List(2) { ownedBy(other) }
            ownedBy(other)
            shared.forEach { assertEquals(201, share(it, actor, other).statusCode()) }
            assertEquals(201, share(owned.first(), other, actor).statusCode())
            assertEquals((owned + shared).sorted(), ids(actor, "all"))
            assertEquals(owned.sorted(), ids(actor, "owned"))
            assertEquals(shared.sorted(), ids(actor, "shared"))
            assertEquals((owned + shared).sorted(), ids(actor, null))
            val listing = json(get("/access?actorId=$actor"))
            assertEquals(actor, listing["actorId"].asString())
            assertTrue(listing["snippets"].values().all { it["relation"].asString() in setOf("owned", "shared") })
        }

        @Test
        fun actorWithoutSnippetsGetsAnEmptyList() {
            val (actor) = actors(1)
            val response = get("/access?actorId=$actor&relation=all")
            assertEquals(200, response.statusCode())
            assertTrue(json(response)["snippets"].isEmpty())
        }

        @Test
        fun invalidQueriesAreRejected() {
            val (owner) = actors(1)
            val id = ownedBy(owner)
            listOf(
                get("/access"),
                get("/access?actorId="),
                get("/access?actorId=%20"),
                get("/access?actorId=$owner&relation=ALL"),
                get("/access?actorId=$owner&relation=mine"),
                get("/ownership/$id/access"),
                get("/ownership/$id/access?actorId=%20"),
                get("/ownership/not-a-uuid/access?actorId=$owner"),
                share(id, "%20", owner),
                share("not-a-uuid", "dev-thiago", owner),
                send("PUT", "/ownership/$id/shares/dev-thiago", "{}"),
                send("PUT", "/ownership/$id/shares/dev-thiago", """{"actorId":" "}"""),
                send("PUT", "/ownership/$id/shares/dev-thiago", """{"actorId":7}"""),
            ).forEach { assertProblem(it, 400, "invalid-request") }
        }

        private fun actors(count: Int): List<String> = List(count) { "actor-${UUID.randomUUID()}" }

        private fun ownedBy(owner: String): String {
            val id = UUID.randomUUID().toString()
            assertEquals(201, put(id, """{"ownerId":"$owner"}""").statusCode())
            return id
        }

        private fun relation(
            id: String,
            actor: String,
        ): String = json(get("/ownership/$id/access?actorId=$actor"))["relation"].asString()

        private fun ids(
            actor: String,
            relation: String?,
        ): List<String> {
            val query = relation?.let { "&relation=$it" }.orEmpty()
            return json(get("/access?actorId=$actor$query"))["snippets"].values().map { it["snippetId"].asString() }
        }

        private fun assertDenied(
            response: HttpResponse<String>,
            relation: String,
        ) {
            assertProblem(response, 403, "share-denied")
            assertEquals(relation, json(response)["relation"].asString())
        }

        private fun assertProblem(
            response: HttpResponse<String>,
            status: Int,
            category: String,
        ) {
            assertEquals(status, response.statusCode(), response.body())
            assertTrue(
                response
                    .headers()
                    .firstValue("Content-Type")
                    .orElseThrow()
                    .startsWith("application/problem+json"),
            )
            assertEquals("urn:permissions:problem:$category", json(response)["type"].asString())
        }
    }
