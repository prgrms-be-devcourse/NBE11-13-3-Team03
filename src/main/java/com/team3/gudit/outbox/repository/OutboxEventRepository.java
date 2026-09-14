package com.team3.gudit.outbox.repository;

import com.team3.gudit.outbox.entity.OutboxEvent;
import com.team3.gudit.outbox.entity.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findAllByStatusOrderByCreatedAtAsc(
            OutboxEventStatus status
    );

    long countByStatus(OutboxEventStatus status);

    Optional<OutboxEvent> findFirstByStatusOrderByCreatedAtAsc(
            OutboxEventStatus status
    );
}