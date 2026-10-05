package com.jjt.ingsis.permissions.configuration

import com.jjt.ingsis.permissions.application.CheckModification
import com.jjt.ingsis.permissions.application.RegisterOwnership
import com.jjt.ingsis.permissions.domain.OwnershipReader
import com.jjt.ingsis.permissions.domain.OwnershipRegistrar
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Configuration
class OwnershipConfiguration {
    @Bean
    fun registerOwnership(registrar: OwnershipRegistrar): RegisterOwnership = RegisterOwnership(registrar)

    @Bean
    fun checkModification(reader: OwnershipReader): CheckModification = CheckModification(reader)

    @Bean
    fun ownershipTransaction(manager: PlatformTransactionManager): TransactionTemplate = TransactionTemplate(manager)
}
