package com.jjt.ingsis.permissions.persistence

import com.jjt.ingsis.permissions.domain.Ownership
import com.jjt.ingsis.permissions.domain.OwnershipLookup
import com.jjt.ingsis.permissions.domain.OwnershipReader
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JdbcOwnershipReader(
    private val jdbc: JdbcTemplate,
) : OwnershipReader {
    override fun find(snippetId: UUID): OwnershipLookup =
        try {
            val owners =
                jdbc.query(
                    "SELECT snippet_id, owner_id FROM snippet_ownership WHERE snippet_id = ?",
                    { row, _ -> Ownership(row.getObject("snippet_id", UUID::class.java), row.getString("owner_id")) },
                    snippetId,
                )
            owners.singleOrNull()?.let(OwnershipLookup::Found) ?: OwnershipLookup.Missing
        } catch (exception: DataAccessException) {
            OwnershipLookup.Failure(exception)
        }
}
