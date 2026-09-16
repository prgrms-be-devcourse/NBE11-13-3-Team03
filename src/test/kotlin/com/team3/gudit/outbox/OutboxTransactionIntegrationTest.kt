package com.team3.gudit.outbox

import com.team3.gudit.outbox.repository.OutboxEventRepository
import com.team3.gudit.outbox.service.OutboxEventService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
class OutboxTransactionIntegrationTest {

    @Autowired
    lateinit var outboxEventService: OutboxEventService

    @Autowired
    lateinit var outboxEventRepository: OutboxEventRepository

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Test
    @DisplayName("DB Transaction이 rollback되면 Outbox 이벤트도 함께 rollback된다")
    fun rollbackOutboxWithTransaction() {
        // given
        val beforeCount = outboxEventRepository.count()

        val transactionTemplate =
            TransactionTemplate(transactionManager)

        // when & then
        assertThatThrownBy {
            transactionTemplate.executeWithoutResult {
                outboxEventService.saveStockRestoreRequested(
                    1L,
                    1L,
                    1L,
                    1
                )

                throw RuntimeException("rollback test")
            }
        }.isInstanceOf(RuntimeException::class.java)

        // then
        assertThat(outboxEventRepository.count())
            .isEqualTo(beforeCount)
    }
}