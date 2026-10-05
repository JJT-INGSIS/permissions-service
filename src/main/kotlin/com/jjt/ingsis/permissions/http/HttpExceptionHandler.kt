package com.jjt.ingsis.permissions.http

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

@RestControllerAdvice
class HttpExceptionHandler(
    private val problems: OwnershipProblems,
) : ResponseEntityExceptionHandler() {
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val invalid = statusCode == HttpStatus.BAD_REQUEST
        val detail = if (invalid) "The request is malformed or contains invalid fields." else "Request failed."
        val category = if (invalid) "invalid-request" else "http-error"
        val problem = ProblemDetail.forStatusAndDetail(statusCode, detail)
        problem.type = URI.create("urn:permissions:problem:$category")
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request)
    }

    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception): ResponseEntity<ProblemDetail> = problems.failure(exception)
}
