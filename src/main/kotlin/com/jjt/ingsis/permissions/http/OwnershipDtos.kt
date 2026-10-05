package com.jjt.ingsis.permissions.http

import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class SnippetIdPath(
    val value: UUID,
)

data class RegisterOwnershipRequest(
    @field:NotBlank val ownerId: String,
)

data class OwnershipResponse(
    val snippetId: UUID,
    val ownerId: String,
)

data class ModificationResponse(
    val allowed: Boolean,
)
