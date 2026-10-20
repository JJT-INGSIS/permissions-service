package com.jjt.ingsis.permissions.persistence

import com.jjt.ingsis.permissions.domain.AccessListing
import com.jjt.ingsis.permissions.domain.AccessLookup
import com.jjt.ingsis.permissions.domain.Ownership
import com.jjt.ingsis.permissions.domain.OwnershipInsert
import com.jjt.ingsis.permissions.domain.OwnershipLookup
import com.jjt.ingsis.permissions.domain.RelationFilter
import com.jjt.ingsis.permissions.domain.ShareInsert
import com.jjt.ingsis.permissions.domain.SnippetShare
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

class JdbcOwnershipFailureTest {
    private val unavailableTransaction =
        mock(TransactionTemplate::class.java) {
            throw CannotCreateTransactionException("database unavailable")
        }
    private val unavailableDatabase =
        mock(JdbcTemplate::class.java) {
            throw DataAccessResourceFailureException("database unavailable")
        }

    @Test
    fun failedTransactionStartIsConvertedToTypedFailure() {
        val registrar = JdbcOwnershipRegistrar(mock(JdbcTemplate::class.java), unavailableTransaction)
        assertInstanceOf(
            OwnershipInsert.Failure::class.java,
            registrar.register(Ownership(UUID.randomUUID(), "dev-thiago")),
        )
    }

    @Test
    fun failedDatabaseReadIsConvertedToTypedFailure() {
        assertInstanceOf(
            OwnershipLookup.Failure::class.java,
            JdbcOwnershipReader(unavailableDatabase).find(UUID.randomUUID()),
        )
    }

    @Test
    fun failedShareTransactionIsConvertedToTypedFailure() {
        val registrar = JdbcShareRegistrar(mock(JdbcTemplate::class.java), unavailableTransaction)
        assertInstanceOf(
            ShareInsert.Failure::class.java,
            registrar.register(SnippetShare(UUID.randomUUID(), "dev-thiago"), "dev-juan"),
        )
    }

    @Test
    fun failedAccessReadsAreConvertedToTypedFailures() {
        val reader = JdbcAccessReader(unavailableDatabase)
        assertInstanceOf(AccessLookup.Failure::class.java, reader.find(UUID.randomUUID(), "dev-juan"))
        assertInstanceOf(AccessListing.Failure::class.java, reader.list("dev-juan", RelationFilter.ALL))
    }
}
