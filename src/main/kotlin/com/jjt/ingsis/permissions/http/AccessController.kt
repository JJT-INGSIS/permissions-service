package com.jjt.ingsis.permissions.http

import com.jjt.ingsis.permissions.application.ShareResult
import com.jjt.ingsis.permissions.application.ShareSnippet
import com.jjt.ingsis.permissions.domain.AccessListing
import com.jjt.ingsis.permissions.domain.AccessLookup
import com.jjt.ingsis.permissions.domain.AccessReader
import com.jjt.ingsis.permissions.domain.SnippetShare
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class AccessController(
    private val reader: AccessReader,
    private val sharing: ShareSnippet,
    private val problems: OwnershipProblems,
) {
    @GetMapping("/access")
    fun list(
        @RequestParam actorId: String,
        @RequestParam(defaultValue = "all") relation: String,
    ): ResponseEntity<*> {
        val filter = relationFilterOf(relation)
        return when {
            actorId.isBlank() -> {
                problems.invalidActor()
            }

            filter == null -> {
                problems.invalidRelation()
            }

            else -> {
                when (val result = reader.list(actorId, filter)) {
                    is AccessListing.Listed -> {
                        ResponseEntity.ok(AccessListResponse(actorId, result.accesses.map(AccessResponse::from)))
                    }

                    is AccessListing.Failure -> {
                        problems.failure(result.cause)
                    }
                }
            }
        }
    }

    @GetMapping("/ownership/{snippetId}/access")
    fun find(
        @PathVariable snippetId: SnippetIdPath,
        @RequestParam actorId: String,
    ): ResponseEntity<*> =
        if (actorId.isBlank()) {
            problems.invalidActor()
        } else {
            when (val result = reader.find(snippetId.value, actorId)) {
                is AccessLookup.Found -> ResponseEntity.ok(AccessResponse.from(result.access))
                AccessLookup.Missing -> problems.missing()
                is AccessLookup.Failure -> problems.failure(result.cause)
            }
        }

    @PutMapping("/ownership/{snippetId}/shares/{recipientId}", consumes = ["application/json"])
    fun share(
        @PathVariable snippetId: SnippetIdPath,
        @PathVariable recipientId: String,
        @Valid @RequestBody request: ShareRequest,
    ): ResponseEntity<*> =
        if (recipientId.isBlank()) {
            problems.invalidRecipientId()
        } else {
            when (val result = sharing.share(SnippetShare(snippetId.value, recipientId), request.actorId)) {
                is ShareResult.Created -> ResponseEntity.status(HttpStatus.CREATED).body(shareResponse(result.share))
                is ShareResult.Existing -> ResponseEntity.ok(shareResponse(result.share))
                is ShareResult.Denied -> problems.shareDenied(result.relation)
                ShareResult.InvalidRecipient -> problems.invalidRecipient()
                ShareResult.Missing -> problems.missing()
                is ShareResult.Failure -> problems.failure(result.cause)
            }
        }

    private fun shareResponse(share: SnippetShare): ShareResponse = ShareResponse(share.snippetId, share.recipientId)
}
