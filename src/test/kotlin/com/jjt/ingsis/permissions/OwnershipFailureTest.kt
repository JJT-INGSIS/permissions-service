package com.jjt.ingsis.permissions

import com.jjt.ingsis.permissions.domain.Ownership
import com.jjt.ingsis.permissions.domain.OwnershipInsert
import com.jjt.ingsis.permissions.domain.OwnershipLookup
import com.jjt.ingsis.permissions.domain.OwnershipReader
import com.jjt.ingsis.permissions.domain.OwnershipRegistrar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.net.http.HttpResponse
import java.util.UUID

@MockitoBean(types = [OwnershipReader::class, OwnershipRegistrar::class])
class OwnershipFailureTest
    @Autowired
    constructor(
        @LocalServerPort port: Int,
        private val reader: OwnershipReader,
        private val registrar: OwnershipRegistrar,
    ) : PostgresHttpTest(port) {
        @Test
        fun unexpectedExceptionsAtHttpBoundaryBecomeSafeProblems() {
            val id = UUID.randomUUID()
            doThrow(
                IllegalStateException("secret SQL password"),
            ).`when`(registrar).register(Ownership(id, "dev-thiago"))
            val response = put(id.toString(), """{"ownerId":"dev-thiago"}""")
            assertTechnicalFailure(response)
        }

        @Test
        fun failedLookupNeverBecomesDeniedPermission() {
            val id = UUID.randomUUID()
            doThrow(IllegalStateException("secret SQL password")).`when`(reader).find(id)
            assertTechnicalFailure(get("/ownership/$id/can-modify?actorId=dev-thiago"))
        }

        @Test
        fun typedFailuresBecomeSafeProblemsForEveryOperation() {
            val id = UUID.randomUUID()
            val cause = IllegalStateException("secret SQL password")
            doReturn(OwnershipInsert.Failure(cause)).`when`(registrar).register(Ownership(id, "dev-thiago"))
            doReturn(OwnershipLookup.Failure(cause)).`when`(reader).find(id)
            assertTechnicalFailure(put(id.toString(), """{"ownerId":"dev-thiago"}"""))
            assertTechnicalFailure(get("/ownership/$id"))
            assertTechnicalFailure(get("/ownership/$id/can-modify?actorId=dev-thiago"))
        }

        private fun assertTechnicalFailure(response: HttpResponse<String>) {
            assertEquals(500, response.statusCode())
            assertEquals("urn:permissions:problem:technical-failure", json(response)["type"].asString())
            assertFalse(response.body().contains("secret SQL password"))
            assertFalse(json(response).has("allowed"))
        }
    }
