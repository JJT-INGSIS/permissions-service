package com.jjt.ingsis.permissions.application

import com.jjt.ingsis.permissions.domain.AccessListing
import com.jjt.ingsis.permissions.domain.AccessLookup
import com.jjt.ingsis.permissions.domain.AccessReader
import com.jjt.ingsis.permissions.domain.AccessRelation
import com.jjt.ingsis.permissions.domain.RelationFilter
import com.jjt.ingsis.permissions.domain.ShareInsert
import com.jjt.ingsis.permissions.domain.ShareRegistrar
import com.jjt.ingsis.permissions.domain.SnippetAccess
import com.jjt.ingsis.permissions.domain.SnippetShare
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class ShareSnippetTest {
    private val share = SnippetShare(UUID.randomUUID(), "dev-thiago")
    private val unexpectedRegistration = ShareRegistrar { _, _ -> error("The share must not be registered") }

    @Test
    fun ownerRegistersTheShareInTheirOwnName() {
        val registrar =
            ShareRegistrar { requested, ownerId ->
                when (ownerId) {
                    "dev-juan" -> ShareInsert.Created(requested)
                    else -> ShareInsert.NotOwner
                }
            }
        val useCase = ShareSnippet(registrar, relationIs(AccessRelation.OWNED))
        assertEquals(ShareResult.Created(share), useCase.share(share, "dev-juan"))
    }

    @Test
    fun repeatedShareIsReportedAsExisting() {
        val registrar = ShareRegistrar { requested, _ -> ShareInsert.Existing(requested) }
        val useCase = ShareSnippet(registrar, relationIs(AccessRelation.OWNED))
        assertEquals(ShareResult.Existing(share), useCase.share(share, "dev-juan"))
    }

    @Test
    fun recipientCannotShareAgain() {
        val useCase = ShareSnippet({ _, _ -> ShareInsert.NotOwner }, relationIs(AccessRelation.SHARED))
        assertEquals(ShareResult.Denied(AccessRelation.SHARED), useCase.share(share, "dev-ana"))
    }

    @Test
    fun actorWithoutAccessCannotShare() {
        val useCase = ShareSnippet({ _, _ -> ShareInsert.NotOwner }, relationIs(AccessRelation.NONE))
        assertEquals(ShareResult.Denied(AccessRelation.NONE), useCase.share(share, "dev-ana"))
    }

    @Test
    fun ownerCannotShareWithThemselves() {
        val useCase = ShareSnippet(unexpectedRegistration, relationIs(AccessRelation.OWNED))
        assertEquals(ShareResult.InvalidRecipient, useCase.share(share, share.recipientId))
    }

    @Test
    fun sharingWithYourselfWithoutOwnershipIsDenied() {
        val useCase = ShareSnippet(unexpectedRegistration, relationIs(AccessRelation.SHARED))
        assertEquals(ShareResult.Denied(AccessRelation.SHARED), useCase.share(share, share.recipientId))
    }

    @Test
    fun snippetWithoutOwnerIsMissing() {
        val useCase = ShareSnippet({ _, _ -> ShareInsert.Missing }, reader(AccessLookup.Missing))
        assertEquals(ShareResult.Missing, useCase.share(share, "dev-juan"))
    }

    @Test
    fun registrationFailureIsNotReportedAsSuccessOrDenial() {
        val cause = IllegalStateException("database unavailable")
        val useCase = ShareSnippet({ _, _ -> ShareInsert.Failure(cause) }, relationIs(AccessRelation.OWNED))
        assertEquals(ShareResult.Failure(cause), useCase.share(share, "dev-juan"))
    }

    @Test
    fun failureWhileExplainingARefusalIsNotADenial() {
        val cause = IllegalStateException("database unavailable")
        val useCase = ShareSnippet({ _, _ -> ShareInsert.NotOwner }, reader(AccessLookup.Failure(cause)))
        assertEquals(ShareResult.Failure(cause), useCase.share(share, "dev-ana"))
    }

    private fun relationIs(relation: AccessRelation): AccessReader =
        reader(AccessLookup.Found(SnippetAccess(share.snippetId, "dev-juan", relation)))

    private fun reader(lookup: AccessLookup): AccessReader =
        object : AccessReader {
            override fun find(
                snippetId: UUID,
                actorId: String,
            ): AccessLookup = lookup

            override fun list(
                actorId: String,
                filter: RelationFilter,
            ): AccessListing = error("Listing is not part of sharing")
        }
}
