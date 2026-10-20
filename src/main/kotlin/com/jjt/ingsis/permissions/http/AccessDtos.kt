package com.jjt.ingsis.permissions.http

import com.jjt.ingsis.permissions.domain.AccessRelation
import com.jjt.ingsis.permissions.domain.RelationFilter
import com.jjt.ingsis.permissions.domain.SnippetAccess
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class ShareRequest(
    @field:NotBlank val actorId: String,
)

data class ShareResponse(
    val snippetId: UUID,
    val recipientId: String,
)

data class AccessResponse(
    val snippetId: UUID,
    val ownerId: String,
    val relation: String,
) {
    companion object {
        fun from(access: SnippetAccess): AccessResponse =
            AccessResponse(access.snippetId, access.ownerId, access.relation.wireName())
    }
}

data class AccessListResponse(
    val actorId: String,
    val snippets: List<AccessResponse>,
)

fun AccessRelation.wireName(): String = name.lowercase()

fun relationFilterOf(value: String): RelationFilter? =
    RelationFilter.entries.firstOrNull {
        it.name.lowercase() == value
    }
