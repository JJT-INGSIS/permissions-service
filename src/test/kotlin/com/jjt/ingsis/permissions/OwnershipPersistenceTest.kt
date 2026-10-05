package com.jjt.ingsis.permissions

import com.jjt.ingsis.permissions.domain.Ownership
import com.jjt.ingsis.permissions.domain.OwnershipInsert
import com.jjt.ingsis.permissions.domain.OwnershipLookup
import com.jjt.ingsis.permissions.domain.OwnershipReader
import com.jjt.ingsis.permissions.domain.OwnershipRegistrar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

class OwnershipPersistenceTest
    @Autowired
    constructor(
        @LocalServerPort port: Int,
        private val registrar: OwnershipRegistrar,
        private val reader: OwnershipReader,
        private val jdbc: JdbcTemplate,
        private val environment: Environment,
    ) : PostgresHttpTest(port) {
        @Test
        fun invalidDatabaseWriteRollsBackAndDoesNotPoisonFollowingOperations() {
            val ownership = Ownership(UUID.randomUUID(), "   ")
            assertInstanceOf(OwnershipInsert.Failure::class.java, registrar.register(ownership))
            assertEquals(OwnershipLookup.Missing, reader.find(ownership.snippetId))
            val valid = ownership.copy(ownerId = "dev-thiago")
            assertEquals(OwnershipInsert.Created(valid), registrar.register(valid))
            assertEquals(OwnershipLookup.Found(valid), reader.find(valid.snippetId))
        }

        @Test
        fun migrationCreatesTheSchemaAndAppliesOnlyOnce() {
            assertEquals(
                1,
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE",
                    Int::class.java,
                ),
            )
        }

        @Test
        fun ownershipSurvivesClosingAndReopeningApplicationContexts() {
            val ownership = Ownership(UUID.randomUUID(), "dev-thiago")
            newContext().use { context ->
                assertEquals(
                    OwnershipInsert.Created(ownership),
                    context.getBean(OwnershipRegistrar::class.java).register(ownership),
                )
            }
            newContext().use { context ->
                assertEquals(
                    OwnershipLookup.Found(ownership),
                    context.getBean(OwnershipReader::class.java).find(ownership.snippetId),
                )
            }
        }

        private fun newContext() =
            SpringApplicationBuilder(PermissionsServiceApplication::class.java)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .run(
                    "--spring.datasource.url=${environment.getRequiredProperty("spring.datasource.url")}",
                    "--spring.datasource.username=${environment.getRequiredProperty("spring.datasource.username")}",
                    "--spring.datasource.password=${environment.getRequiredProperty("spring.datasource.password")}",
                )
    }
