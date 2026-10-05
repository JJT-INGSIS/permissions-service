package com.jjt.ingsis.permissions.persistence

import com.jjt.ingsis.permissions.domain.Ownership
import com.jjt.ingsis.permissions.domain.OwnershipInsert
import com.jjt.ingsis.permissions.domain.OwnershipLookup
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

class JdbcOwnershipFailureTest {
    @Test
    fun failedTransactionStartIsConvertedToTypedFailure() {
        val transaction =
            mock(TransactionTemplate::class.java) {
                throw CannotCreateTransactionException("database unavailable")
            }
        val registrar = JdbcOwnershipRegistrar(mock(JdbcTemplate::class.java), transaction)
        assertInstanceOf(
            OwnershipInsert.Failure::class.java,
            registrar.register(Ownership(UUID.randomUUID(), "dev-thiago")),
        )
    }

    @Test
    fun failedDatabaseReadIsConvertedToTypedFailure() {
        val jdbc =
            mock(JdbcTemplate::class.java) {
                throw DataAccessResourceFailureException("database unavailable")
            }
        assertInstanceOf(OwnershipLookup.Failure::class.java, JdbcOwnershipReader(jdbc).find(UUID.randomUUID()))
    }
}
