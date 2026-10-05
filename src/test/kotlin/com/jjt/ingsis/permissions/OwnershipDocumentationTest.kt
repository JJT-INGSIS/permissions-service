package com.jjt.ingsis.permissions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path

class OwnershipDocumentationTest
    @Autowired
    constructor(
        @LocalServerPort port: Int,
    ) : PostgresHttpTest(port) {
        @Test
        fun documentedJsonExamplesMatchRealHttpResponses() {
            val examples =
                Regex("```json\\n([\\s\\S]*?)\\n```")
                    .findAll(Files.readString(Path.of("docs/ownership.md")))
                    .map { it.groupValues[1] }
                    .toList()
            assertEquals(5, examples.size)
            val mapper = JsonMapper.builder().build()
            val expected = examples.map(mapper::readTree)
            val id = expected[1]["snippetId"].asString()
            val created = put(id, examples[0])
            assertEquals(201, created.statusCode())
            assertEquals(expected[1], json(created))
            val repeated = put(id, examples[0])
            assertEquals(200, repeated.statusCode())
            assertEquals(expected[1], json(repeated))
            assertEquals(expected[1], json(get("/ownership/$id")))
            assertEquals(expected[2], json(get("/ownership/$id/can-modify?actorId=dev-thiago")))
            assertEquals(expected[3], json(get("/ownership/$id/can-modify?actorId=dev-other")))
            val conflict = put(id, """{"ownerId":"dev-other"}""")
            assertEquals(409, conflict.statusCode())
            assertEquals(expected[4], json(conflict))
        }
    }
