package com.jjt.ingsis.permissions.persistence

import com.jjt.ingsis.permissions.domain.Ownership
import com.jjt.ingsis.permissions.domain.OwnershipInsert
import com.jjt.ingsis.permissions.domain.OwnershipRegistrar
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.TransactionException
import org.springframework.transaction.support.TransactionTemplate

@Repository
class JdbcOwnershipRegistrar(
    private val jdbc: JdbcTemplate,
    private val transaction: TransactionTemplate,
) : OwnershipRegistrar {
    override fun register(ownership: Ownership): OwnershipInsert =
        try {
            transaction.execute { insertOrFind(ownership) }
        } catch (exception: DataAccessException) {
            OwnershipInsert.Failure(exception)
        } catch (exception: TransactionException) {
            OwnershipInsert.Failure(exception)
        }

    private fun insertOrFind(ownership: Ownership): OwnershipInsert {
        val inserted =
            jdbc.update(
                "INSERT INTO snippet_ownership (snippet_id, owner_id) VALUES (?, ?) " +
                    "ON CONFLICT (snippet_id) DO NOTHING",
                ownership.snippetId,
                ownership.ownerId,
            )
        return if (inserted == 1) {
            OwnershipInsert.Created(ownership)
        } else {
            val persisted =
                jdbc.queryForObject(
                    "SELECT owner_id FROM snippet_ownership WHERE snippet_id = ?",
                    String::class.java,
                    ownership.snippetId,
                )
            OwnershipInsert.Existing(ownership.copy(ownerId = requireNotNull(persisted)))
        }
    }
}
