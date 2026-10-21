package com.jjt.ingsis.permissions.http

import com.jjt.ingsis.permissions.domain.AccessRelation
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import java.net.URI

@Component
class OwnershipProblems {
    private val logger = LoggerFactory.getLogger(OwnershipProblems::class.java)

    fun conflict(): ResponseEntity<ProblemDetail> =
        response(HttpStatus.CONFLICT, "owner-conflict", "The snippet already has a different owner.")

    fun missing(): ResponseEntity<ProblemDetail> =
        response(HttpStatus.NOT_FOUND, "ownership-not-found", "No ownership is registered for this snippet.")

    fun invalidActor(): ResponseEntity<ProblemDetail> =
        response(HttpStatus.BAD_REQUEST, "invalid-request", "actorId must not be blank.")

    fun invalidRelation(): ResponseEntity<ProblemDetail> =
        response(HttpStatus.BAD_REQUEST, "invalid-request", "relation must be all, owned or shared.")

    fun invalidRecipientId(): ResponseEntity<ProblemDetail> =
        response(HttpStatus.BAD_REQUEST, "invalid-request", "recipientId must not be blank.")

    fun shareDenied(relation: AccessRelation): ResponseEntity<ProblemDetail> {
        val problem = problem(HttpStatus.FORBIDDEN, "share-denied", "Only the owner can share this snippet.")
        problem.setProperty("relation", relation.wireName())
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem)
    }

    fun invalidRecipient(): ResponseEntity<ProblemDetail> =
        response(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "invalid-recipient",
            "The owner cannot share a snippet with themselves.",
        )

    fun failure(cause: Exception): ResponseEntity<ProblemDetail> {
        logger.error("Ownership operation failed", cause)
        return response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "technical-failure",
            "The ownership operation could not complete.",
        )
    }

    private fun response(
        status: HttpStatus,
        category: String,
        detail: String,
    ): ResponseEntity<ProblemDetail> = ResponseEntity.status(status).body(problem(status, category, detail))

    private fun problem(
        status: HttpStatus,
        category: String,
        detail: String,
    ): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(status, detail)
        problem.type = URI.create("urn:permissions:problem:$category")
        return problem
    }
}
