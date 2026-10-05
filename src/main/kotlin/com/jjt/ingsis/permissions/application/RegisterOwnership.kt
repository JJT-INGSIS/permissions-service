package com.jjt.ingsis.permissions.application

import com.jjt.ingsis.permissions.domain.Ownership
import com.jjt.ingsis.permissions.domain.OwnershipInsert
import com.jjt.ingsis.permissions.domain.OwnershipRegistrar

sealed interface RegistrationResult {
    data class Created(
        val ownership: Ownership,
    ) : RegistrationResult

    data class Existing(
        val ownership: Ownership,
    ) : RegistrationResult

    data object Conflict : RegistrationResult

    data class Failure(
        val cause: Exception,
    ) : RegistrationResult
}

class RegisterOwnership(
    private val registrar: OwnershipRegistrar,
) {
    fun register(ownership: Ownership): RegistrationResult =
        when (val result = registrar.register(ownership)) {
            is OwnershipInsert.Created -> {
                RegistrationResult.Created(result.ownership)
            }

            is OwnershipInsert.Existing -> {
                if (result.ownership.ownerId == ownership.ownerId) {
                    RegistrationResult.Existing(result.ownership)
                } else {
                    RegistrationResult.Conflict
                }
            }

            is OwnershipInsert.Failure -> {
                RegistrationResult.Failure(result.cause)
            }
        }
}
