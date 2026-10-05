package com.jjt.ingsis.permissions.configuration

import com.jjt.ingsis.permissions.http.SnippetIdConverter
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.format.FormatterRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.type.LogicalType

@Configuration
class HttpInputConfiguration(
    private val snippetIdConverter: SnippetIdConverter,
) : WebMvcConfigurer {
    override fun addFormatters(registry: FormatterRegistry) {
        registry.addConverter(snippetIdConverter)
    }

    @Bean
    fun strictJsonStrings(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder ->
            builder.withCoercionConfig(LogicalType.Textual) { coercion ->
                listOf(CoercionInputShape.Integer, CoercionInputShape.Float, CoercionInputShape.Boolean)
                    .forEach { coercion.setCoercion(it, CoercionAction.Fail) }
            }
        }
}
