package com.team3.gudit.outbox;

import com.team3.gudit.outbox.repository.OutboxEventRepository;
import com.team3.gudit.outbox.service.OutboxEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OutboxTransactionIntegrationTest {

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("DB Transaction이 rollback되면 Outbox 이벤트도 함께 rollback된다")
    void rollbackOutboxWithTransaction() {
        // given
        long beforeCount = outboxEventRepository.count();

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        // when & then
        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    outboxEventService.saveStockRestoreRequested(
                            1L,
                            1L,
                            1L,
                            1
                    );

                    throw new RuntimeException("rollback test");
                })
        ).isInstanceOf(RuntimeException.class);

        // then
        assertThat(outboxEventRepository.count())
                .isEqualTo(beforeCount);
    }
}