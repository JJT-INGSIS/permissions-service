package com.jjt.ingsis.permissions.http

import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class SnippetIdConverter : Converter<String, SnippetIdPath> {
    override fun convert(source: String): SnippetIdPath {
        require(UUID_PATTERN.matches(source)) { "snippetId must be a UUID." }
        return SnippetIdPath(UUID.fromString(source))
    }

    private companion object {
        val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
