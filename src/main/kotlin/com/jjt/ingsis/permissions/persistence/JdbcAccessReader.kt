package com.jjt.ingsis.permissions.persistence

import com.jjt.ingsis.permissions.domain.AccessListing
import com.jjt.ingsis.permissions.domain.AccessLookup
import com.jjt.ingsis.permissions.domain.AccessReader
import com.jjt.ingsis.permissions.domain.AccessRelation
import com.jjt.ingsis.permissions.domain.RelationFilter
import com.jjt.ingsis.permissions.domain.SnippetAccess
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JdbcAccessReader(
    private val jdbc: JdbcTemplate,
) : AccessReader {
    override fun find(
        snippetId: UUID,
        actorId: String,
    ): AccessLookup =
        try {
            val accesses = jdbc.query("$ACCESS WHERE o.snippet_id = ?", accessOf(actorId), actorId, snippetId)
            accesses.singleOrNull()?.let(AccessLookup::Found) ?: AccessLookup.Missing
        } catch (exception: DataAccessException) {
            AccessLookup.Failure(exception)
        }

    override fun list(
        actorId: String,
        filter: RelationFilter,
    ): AccessListing =
        try {
            val sql = "$ACCESS WHERE ${condition(filter)} ORDER BY o.snippet_id"
            AccessListing.Listed(jdbc.query(sql, accessOf(actorId), actorId, actorId))
        } catch (exception: DataAccessException) {
            AccessListing.Failure(exception)
        }

    private fun accessOf(actorId: String): RowMapper<SnippetAccess> =
        RowMapper { row, _ ->
            val ownerId = row.getString("owner_id")
            SnippetAccess(
                snippetId = row.getObject("snippet_id", UUID::class.java),
                ownerId = ownerId,
                relation = AccessRelation.of(ownerId, actorId, row.getBoolean("shared")),
            )
        }

    private fun condition(filter: RelationFilter): String =
        when (filter) {
            RelationFilter.ALL -> "(o.owner_id = ? OR s.recipient_id IS NOT NULL)"
            RelationFilter.OWNED -> "o.owner_id = ?"
            RelationFilter.SHARED -> "(o.owner_id <> ? AND s.recipient_id IS NOT NULL)"
        }

    private companion object {
        const val ACCESS =
            "SELECT o.snippet_id, o.owner_id, s.recipient_id IS NOT NULL AS shared " +
                "FROM snippet_ownership o " +
                "LEFT JOIN snippet_shares s ON s.snippet_id = o.snippet_id AND s.recipient_id = ?"
    }
}
