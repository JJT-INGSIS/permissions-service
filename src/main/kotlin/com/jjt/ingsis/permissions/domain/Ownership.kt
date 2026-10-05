package com.jjt.ingsis.permissions.domain

import java.util.UUID

data class Ownership(
    val snippetId: UUID,
    val ownerId: String,
)

sealed interface OwnershipLookup {
    data class Found(
        val ownership: Ownership,
    ) : OwnershipLookup

    data object Missing : OwnershipLookup

    data class Failure(
        val cause: Exception,
    ) : OwnershipLookup
}

sealed interface OwnershipInsert {
    data class Created(
        val ownership: Ownership,
    ) : OwnershipInsert

    data class Existing(
        val ownership: Ownership,
    ) : OwnershipInsert

    data class Failure(
        val cause: Exception,
    ) : OwnershipInsert
}

fun interface OwnershipReader {
    fun find(snippetId: UUID): OwnershipLookup
}

fun interface OwnershipRegistrar {
    fun register(ownership: Ownership): OwnershipInsert
}
