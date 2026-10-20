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

class ShareConcurrencyTest
    @Autowired
    constructor(
        @LocalServerPort port: Int,
        private val jdbc: JdbcTemplate,
    ) : PostgresHttpTest(port) {
        @Test
        fun concurrentRetriesCreateExactlyOneShare() {
            val id = UUID.randomUUID()
            val owner = "owner-$id"
            assertEquals(201, put(id.toString(), """{"ownerId":"$owner"}""").statusCode())
            val statuses = race(RACERS) { share(id.toString(), "dev-thiago", owner).statusCode() }
            assertEquals(1, statuses.count { it == 201 })
            assertEquals(RACERS - 1, statuses.count { it == 200 })
            assertEquals(1, shares(id))
        }

        @Test
        fun concurrentNonOwnersNeverCreateShares() {
            val id = UUID.randomUUID()
            assertEquals(201, put(id.toString(), """{"ownerId":"owner-$id"}""").statusCode())
            val statuses = race(RACERS) { share(id.toString(), "dev-thiago", "intruder-$it").statusCode() }
            assertEquals(List(RACERS) { 403 }, statuses)
            assertEquals(0, shares(id))
        }

        private fun race(
            racers: Int,
            attempt: (Int) -> Int,
        ): List<Int> =
            Executors.newFixedThreadPool(racers).use { executor ->
                val ready = CountDownLatch(racers)
                val start = CountDownLatch(1)
                val futures =
                    List(racers) { index ->
                        executor.submit(
                            Callable {
                                ready.countDown()
                                check(start.await(10, TimeUnit.SECONDS))
                                attempt(index)
                            },
                        )
                    }
                check(ready.await(10, TimeUnit.SECONDS))
                start.countDown()
                futures.map { it.get(30, TimeUnit.SECONDS) }
            }

        private fun shares(id: UUID): Int? =
            jdbc.queryForObject("SELECT COUNT(*) FROM snippet_shares WHERE snippet_id = ?", Int::class.java, id)

        private companion object {
            const val RACERS = 8
        }
    }
