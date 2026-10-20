package com.jjt.ingsis.permissions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path

class AccessDocumentationTest
    @Autowired
    constructor(
        @LocalServerPort port: Int,
    ) : PostgresHttpTest(port) {
        @Test
        fun documentedJsonExamplesMatchRealHttpResponses() {
            val examples =
                Regex("```json\\r?\\n([\\s\\S]*?)\\r?\\n```")
                    .findAll(Files.readString(Path.of("docs/access.md")))
                    .map { it.groupValues[1] }
                    .toList()
            assertEquals(5, examples.size)
            val expected = examples.map(JsonMapper.builder().build()::readTree)
            val owned = expected[0]["snippets"][0]
            val shared = expected[0]["snippets"][1]
            val actor = expected[0]["actorId"].asString()
            val ownedId = owned["snippetId"].asString()
            val sharedId = shared["snippetId"].asString()
            val recipient = expected[3]["recipientId"].asString()
            assertEquals(201, put(ownedId, """{"ownerId":"$actor"}""").statusCode())
            assertEquals(201, put(sharedId, """{"ownerId":"${shared["ownerId"].asString()}"}""").statusCode())
            val created = send("PUT", "/ownership/$ownedId/shares/$recipient", examples[2])
            assertEquals(201, created.statusCode())
            assertEquals(expected[3], json(created))
            assertEquals(201, share(sharedId, actor, shared["ownerId"].asString()).statusCode())
            assertEquals(expected[0], json(get("/access?actorId=$actor&relation=all")))
            assertEquals(expected[1], json(get("/ownership/$sharedId/access?actorId=$actor")))
            val denied = share(ownedId, "dev-ana", recipient)
            assertEquals(403, denied.statusCode())
            assertEquals(expected[4], json(denied))
        }
    }
