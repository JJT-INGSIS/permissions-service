package com.jjt.ingsis.permissions

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
open class PostgresHttpTest(
    private val port: Int,
) {
    protected fun get(path: String): HttpResponse<String> = send("GET", path)

    protected fun put(
        snippetId: String,
        body: String,
    ): HttpResponse<String> = send("PUT", "/ownership/$snippetId", body)

    protected fun json(response: HttpResponse<String>): JsonNode = mapper.readTree(response.body())

    protected fun send(
        method: String,
        path: String,
        body: String = "",
        contentType: String = "application/json",
    ): HttpResponse<String> =
        client.send(
            HttpRequest
                .newBuilder(URI.create("http://localhost:$port$path"))
                .header("Content-Type", contentType)
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    companion object {
        private val postgres by lazy { PostgreSQLContainer("postgres:18.6").apply { start() } }
        private val client = HttpClient.newHttpClient()
        private val mapper = JsonMapper.builder().build()

        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
