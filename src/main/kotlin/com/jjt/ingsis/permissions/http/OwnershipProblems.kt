package com.jjt.ingsis.permissions.http

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
    ): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(status, detail)
        problem.type = URI.create("urn:permissions:problem:$category")
        return ResponseEntity.status(status).body(problem)
    }
}
