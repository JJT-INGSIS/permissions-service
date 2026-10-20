package com.jjt.ingsis.permissions.domain

import java.util.UUID

data class SnippetShare(
    val snippetId: UUID,
    val recipientId: String,
)

sealed interface ShareInsert {
    data class Created(
        val share: SnippetShare,
    ) : ShareInsert

    data class Existing(
        val share: SnippetShare,
    ) : ShareInsert

    data object NotOwner : ShareInsert

    data object Missing : ShareInsert

    data class Failure(
        val cause: Exception,
    ) : ShareInsert
}

fun interface ShareRegistrar {
    fun register(
        share: SnippetShare,
        ownerId: String,
    ): ShareInsert
}
