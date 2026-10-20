package com.jjt.ingsis.permissions.persistence

import com.jjt.ingsis.permissions.domain.ShareInsert
import com.jjt.ingsis.permissions.domain.ShareRegistrar
import com.jjt.ingsis.permissions.domain.SnippetShare
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.TransactionException
import org.springframework.transaction.support.TransactionTemplate

@Repository
class JdbcShareRegistrar(
    private val jdbc: JdbcTemplate,
    private val transaction: TransactionTemplate,
) : ShareRegistrar {
    override fun register(
        share: SnippetShare,
        ownerId: String,
    ): ShareInsert =
        try {
            transaction.execute { insertOrExplain(share, ownerId) }
        } catch (exception: DataAccessException) {
            ShareInsert.Failure(exception)
        } catch (exception: TransactionException) {
            ShareInsert.Failure(exception)
        }

    private fun insertOrExplain(
        share: SnippetShare,
        ownerId: String,
    ): ShareInsert {
        val inserted =
            jdbc.update(
                "INSERT INTO snippet_shares (snippet_id, recipient_id) " +
                    "SELECT snippet_id, ? FROM snippet_ownership WHERE snippet_id = ? AND owner_id = ? " +
                    "ON CONFLICT (snippet_id, recipient_id) DO NOTHING",
                share.recipientId,
                share.snippetId,
                ownerId,
            )
        return if (inserted == 1) ShareInsert.Created(share) else explain(share, ownerId)
    }

    private fun explain(
        share: SnippetShare,
        ownerId: String,
    ): ShareInsert {
        val owners =
            jdbc.queryForList(
                "SELECT owner_id FROM snippet_ownership WHERE snippet_id = ?",
                String::class.java,
                share.snippetId,
            )
        return when (owners.singleOrNull()) {
            null -> ShareInsert.Missing
            ownerId -> ShareInsert.Existing(share)
            else -> ShareInsert.NotOwner
        }
    }
}
