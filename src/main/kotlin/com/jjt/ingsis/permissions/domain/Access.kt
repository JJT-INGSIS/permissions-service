package com.jjt.ingsis.permissions.domain

import java.util.UUID

enum class AccessRelation {
    OWNED,
    SHARED,
    NONE,
    ;

    companion object {
        fun of(
            ownerId: String,
            actorId: String,
            shared: Boolean,
        ): AccessRelation =
            when {
                ownerId == actorId -> OWNED
                shared -> SHARED
                else -> NONE
            }
    }
}

enum class RelationFilter {
    ALL,
    OWNED,
    SHARED,
}

data class SnippetAccess(
    val snippetId: UUID,
    val ownerId: String,
    val relation: AccessRelation,
)

sealed interface AccessLookup {
    data class Found(
        val access: SnippetAccess,
    ) : AccessLookup

    data object Missing : AccessLookup

    data class Failure(
        val cause: Exception,
    ) : AccessLookup
}

sealed interface AccessListing {
    data class Listed(
        val accesses: List<SnippetAccess>,
    ) : AccessListing

    data class Failure(
        val cause: Exception,
    ) : AccessListing
}

interface AccessReader {
    fun find(
        snippetId: UUID,
        actorId: String,
    ): AccessLookup

    fun list(
        actorId: String,
        filter: RelationFilter,
    ): AccessListing
}
