package com.jjt.ingsis.permissions.application

import com.jjt.ingsis.permissions.domain.OwnershipLookup
import com.jjt.ingsis.permissions.domain.OwnershipReader
import java.util.UUID

sealed interface ModificationResult {
    data class Checked(
        val allowed: Boolean,
    ) : ModificationResult

    data object Missing : ModificationResult

    data class Failure(
        val cause: Exception,
    ) : ModificationResult
}

class CheckModification(
    private val reader: OwnershipReader,
) {
    fun check(
        snippetId: UUID,
        actorId: String,
    ): ModificationResult =
        when (val result = reader.find(snippetId)) {
            is OwnershipLookup.Found -> ModificationResult.Checked(result.ownership.ownerId == actorId)
            OwnershipLookup.Missing -> ModificationResult.Missing
            is OwnershipLookup.Failure -> ModificationResult.Failure(result.cause)
        }
}
