package com.jjt.ingsis.permissions.http

import com.jjt.ingsis.permissions.application.CheckModification
import com.jjt.ingsis.permissions.application.ModificationResult
import com.jjt.ingsis.permissions.application.RegisterOwnership
import com.jjt.ingsis.permissions.application.RegistrationResult
import com.jjt.ingsis.permissions.domain.Ownership
import com.jjt.ingsis.permissions.domain.OwnershipLookup
import com.jjt.ingsis.permissions.domain.OwnershipReader
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/ownership")
class OwnershipController(
    private val registration: RegisterOwnership,
    private val reader: OwnershipReader,
    private val modification: CheckModification,
    private val problems: OwnershipProblems,
) {
    @PutMapping("/{snippetId}", consumes = ["application/json"])
    fun register(
        @PathVariable snippetId: SnippetIdPath,
        @Valid @RequestBody request: RegisterOwnershipRequest,
    ): ResponseEntity<*> =
        when (val result = registration.register(Ownership(snippetId.value, request.ownerId))) {
            is RegistrationResult.Created -> {
                ResponseEntity
                    .created(URI.create("/ownership/${snippetId.value}"))
                    .body(OwnershipResponse(result.ownership.snippetId, result.ownership.ownerId))
            }

            is RegistrationResult.Existing -> {
                ResponseEntity.ok(OwnershipResponse(result.ownership.snippetId, result.ownership.ownerId))
            }

            RegistrationResult.Conflict -> {
                problems.conflict()
            }

            is RegistrationResult.Failure -> {
                problems.failure(result.cause)
            }
        }

    @GetMapping("/{snippetId}")
    fun get(
        @PathVariable snippetId: SnippetIdPath,
    ): ResponseEntity<*> =
        when (val result = reader.find(snippetId.value)) {
            is OwnershipLookup.Found -> {
                ResponseEntity.ok(OwnershipResponse(result.ownership.snippetId, result.ownership.ownerId))
            }

            OwnershipLookup.Missing -> {
                problems.missing()
            }

            is OwnershipLookup.Failure -> {
                problems.failure(result.cause)
            }
        }

    @GetMapping("/{snippetId}/can-modify")
    fun canModify(
        @PathVariable snippetId: SnippetIdPath,
        @RequestParam actorId: String,
    ): ResponseEntity<*> =
        if (actorId.isBlank()) {
            problems.invalidActor()
        } else {
            when (val result = modification.check(snippetId.value, actorId)) {
                is ModificationResult.Checked -> ResponseEntity.ok(ModificationResponse(result.allowed))
                ModificationResult.Missing -> problems.missing()
                is ModificationResult.Failure -> problems.failure(result.cause)
            }
        }
}
