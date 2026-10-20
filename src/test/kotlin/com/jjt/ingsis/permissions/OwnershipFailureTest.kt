package com.jjt.ingsis.permissions

import com.jjt.ingsis.permissions.domain.AccessListing
import com.jjt.ingsis.permissions.domain.AccessLookup
import com.jjt.ingsis.permissions.domain.AccessReader
import com.jjt.ingsis.permissions.domain.Ownership
import com.jjt.ingsis.permissions.domain.OwnershipInsert
import com.jjt.ingsis.permissions.domain.OwnershipLookup
import com.jjt.ingsis.permissions.domain.OwnershipReader
import com.jjt.ingsis.permissions.domain.OwnershipRegistrar
import com.jjt.ingsis.permissions.domain.RelationFilter
import com.jjt.ingsis.permissions.domain.ShareInsert
import com.jjt.ingsis.permissions.domain.ShareRegistrar
import com.jjt.ingsis.permissions.domain.SnippetShare
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

@MockitoBean(types = [OwnershipReader::class, OwnershipRegistrar::class, AccessReader::class, ShareRegistrar::class])
class OwnershipFailureTest
    @Autowired
    constructor(
        @LocalServerPort port: Int,
        private val reader: OwnershipReader,
        private val registrar: OwnershipRegistrar,
        private val accessReader: AccessReader,
        private val shareRegistrar: ShareRegistrar,
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

        @Test
        fun accessFailuresAreNeverAnEmptyListOrANoAccessAnswer() {
            val id = UUID.randomUUID()
            val cause = IllegalStateException("secret SQL password")
            doReturn(AccessListing.Failure(cause)).`when`(accessReader).list("dev-juan", RelationFilter.ALL)
            doReturn(AccessLookup.Failure(cause)).`when`(accessReader).find(id, "dev-juan")
            assertTechnicalFailure(get("/access?actorId=dev-juan"))
            assertTechnicalFailure(get("/ownership/$id/access?actorId=dev-juan"))
        }

        @Test
        fun sharingFailuresAreNeverConfirmedOrDenied() {
            val id = UUID.randomUUID()
            val cause = IllegalStateException("secret SQL password")
            doReturn(
                ShareInsert.Failure(cause),
            ).`when`(shareRegistrar).register(SnippetShare(id, "dev-thiago"), "dev-juan")
            assertTechnicalFailure(share(id.toString(), "dev-thiago", "dev-juan"))
            doReturn(ShareInsert.NotOwner).`when`(shareRegistrar).register(SnippetShare(id, "dev-thiago"), "dev-ana")
            doThrow(IllegalStateException("secret SQL password")).`when`(accessReader).find(id, "dev-ana")
            assertTechnicalFailure(share(id.toString(), "dev-thiago", "dev-ana"))
        }

        private fun assertTechnicalFailure(response: HttpResponse<String>) {
            assertEquals(500, response.statusCode())
            assertEquals("urn:permissions:problem:technical-failure", json(response)["type"].asString())
            assertFalse(response.body().contains("secret SQL password"))
            assertFalse(json(response).has("allowed"))
            assertFalse(json(response).has("snippets"))
            assertFalse(json(response).has("relation"))
            assertFalse(json(response).has("recipientId"))
        }
    }
