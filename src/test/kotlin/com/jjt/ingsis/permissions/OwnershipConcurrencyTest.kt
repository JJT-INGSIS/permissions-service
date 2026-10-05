package com.jjt.ingsis.permissions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OwnershipConcurrencyTest
    @Autowired
    constructor(
        @LocalServerPort port: Int,
        private val jdbc: JdbcTemplate,
    ) : PostgresHttpTest(port) {
        @Test
        fun concurrentRetriesCreateExactlyOneRelation() {
            val id = UUID.randomUUID()
            val statuses = race(id, List(8) { "dev-thiago" })
            assertEquals(1, statuses.count { it == 201 })
            assertEquals(7, statuses.count { it == 200 })
            assertEquals(1, count(id))
            assertEquals("dev-thiago", json(get("/ownership/$id"))["ownerId"].asString())
        }

        @Test
        fun competingOwnersCannotOverwriteEachOther() {
            val id = UUID.randomUUID()
            val statuses = race(id, List(8) { "actor-$it" })
            assertEquals(1, statuses.count { it == 201 })
            assertEquals(7, statuses.count { it == 409 })
            assertEquals(1, count(id))
        }

        private fun race(
            id: UUID,
            owners: List<String>,
        ): List<Int> =
            Executors.newFixedThreadPool(owners.size).use { executor ->
                val ready = CountDownLatch(owners.size)
                val start = CountDownLatch(1)
                val futures =
                    owners.map { owner ->
                        executor.submit(
                            Callable {
                                ready.countDown()
                                check(start.await(10, TimeUnit.SECONDS))
                                put(id.toString(), """{"ownerId":"$owner"}""").statusCode()
                            },
                        )
                    }
                check(ready.await(10, TimeUnit.SECONDS))
                start.countDown()
                futures.map { it.get(30, TimeUnit.SECONDS) }
            }

        private fun count(id: UUID): Int? =
            jdbc.queryForObject("SELECT COUNT(*) FROM snippet_ownership WHERE snippet_id = ?", Int::class.java, id)
    }
