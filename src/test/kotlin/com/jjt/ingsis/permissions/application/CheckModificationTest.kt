package com.jjt.ingsis.permissions.application

import com.jjt.ingsis.permissions.domain.Ownership
import com.jjt.ingsis.permissions.domain.OwnershipLookup
import com.jjt.ingsis.permissions.domain.OwnershipReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class CheckModificationTest {
    private val ownership = Ownership(UUID.randomUUID(), "dev-thiago")

    @Test
    fun ownerIsAllowed() {
        val useCase = CheckModification(OwnershipReader { OwnershipLookup.Found(ownership) })
        assertEquals(ModificationResult.Checked(true), useCase.check(ownership.snippetId, ownership.ownerId))
    }

    @Test
    fun anotherActorIsDenied() {
        val useCase = CheckModification(OwnershipReader { OwnershipLookup.Found(ownership) })
        assertEquals(ModificationResult.Checked(false), useCase.check(ownership.snippetId, "someone-else"))
    }

    @Test
    fun missingRelationIsExplicit() {
        val useCase = CheckModification(OwnershipReader { OwnershipLookup.Missing })
        assertEquals(ModificationResult.Missing, useCase.check(ownership.snippetId, ownership.ownerId))
    }

    @Test
    fun technicalFailureIsNotDeniedPermission() {
        val cause = IllegalStateException("database unavailable")
        val useCase = CheckModification(OwnershipReader { OwnershipLookup.Failure(cause) })
        assertEquals(ModificationResult.Failure(cause), useCase.check(ownership.snippetId, ownership.ownerId))
    }
}
