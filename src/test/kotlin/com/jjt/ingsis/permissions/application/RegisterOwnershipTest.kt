package com.jjt.ingsis.permissions.application

import com.jjt.ingsis.permissions.domain.Ownership
import com.jjt.ingsis.permissions.domain.OwnershipInsert
import com.jjt.ingsis.permissions.domain.OwnershipRegistrar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class RegisterOwnershipTest {
    private val ownership = Ownership(UUID.randomUUID(), "dev-thiago")

    @Test
    fun createdOwnershipIsReported() {
        val useCase = RegisterOwnership(OwnershipRegistrar { OwnershipInsert.Created(it) })
        assertEquals(RegistrationResult.Created(ownership), useCase.register(ownership))
    }

    @Test
    fun sameOwnerCanRetry() {
        val useCase = RegisterOwnership(OwnershipRegistrar { OwnershipInsert.Existing(ownership) })
        assertEquals(RegistrationResult.Existing(ownership), useCase.register(ownership))
    }

    @Test
    fun differentOwnerIsAConflict() {
        val useCase = RegisterOwnership(OwnershipRegistrar { OwnershipInsert.Existing(ownership) })
        assertEquals(RegistrationResult.Conflict, useCase.register(ownership.copy(ownerId = "someone-else")))
    }

    @Test
    fun persistenceFailureIsNotReportedAsSuccess() {
        val cause = IllegalStateException("database unavailable")
        val useCase = RegisterOwnership(OwnershipRegistrar { OwnershipInsert.Failure(cause) })
        assertEquals(RegistrationResult.Failure(cause), useCase.register(ownership))
    }
}
