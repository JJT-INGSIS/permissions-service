package com.jjt.ingsis.permissions.application

import com.jjt.ingsis.permissions.domain.AccessLookup
import com.jjt.ingsis.permissions.domain.AccessReader
import com.jjt.ingsis.permissions.domain.AccessRelation
import com.jjt.ingsis.permissions.domain.ShareInsert
import com.jjt.ingsis.permissions.domain.ShareRegistrar
import com.jjt.ingsis.permissions.domain.SnippetShare

sealed interface ShareResult {
    data class Created(
        val share: SnippetShare,
    ) : ShareResult

    data class Existing(
        val share: SnippetShare,
    ) : ShareResult

    data class Denied(
        val relation: AccessRelation,
    ) : ShareResult

    data object InvalidRecipient : ShareResult

    data object Missing : ShareResult

    data class Failure(
        val cause: Exception,
    ) : ShareResult
}

class ShareSnippet(
    private val registrar: ShareRegistrar,
    private val reader: AccessReader,
) {
    fun share(
        share: SnippetShare,
        actorId: String,
    ): ShareResult =
        if (share.recipientId == actorId) {
            refusal(share, actorId)
        } else {
            when (val result = registrar.register(share, ownerId = actorId)) {
                is ShareInsert.Created -> ShareResult.Created(result.share)
                is ShareInsert.Existing -> ShareResult.Existing(result.share)
                ShareInsert.NotOwner -> refusal(share, actorId)
                ShareInsert.Missing -> ShareResult.Missing
                is ShareInsert.Failure -> ShareResult.Failure(result.cause)
            }
        }

    private fun refusal(
        share: SnippetShare,
        actorId: String,
    ): ShareResult =
        when (val result = reader.find(share.snippetId, actorId)) {
            is AccessLookup.Found -> refusalFor(result.access.relation)
            AccessLookup.Missing -> ShareResult.Missing
            is AccessLookup.Failure -> ShareResult.Failure(result.cause)
        }

    private fun refusalFor(relation: AccessRelation): ShareResult =
        if (relation == AccessRelation.OWNED) ShareResult.InvalidRecipient else ShareResult.Denied(relation)
}
