package com.team3.gudit.outbox.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import static com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.STOCK_RESTORE_GROUP;
import static com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.STOCK_RESTORE_STREAM;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockRestoreConsumerGroupInitializer implements ApplicationRunner {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            stringRedisTemplate.opsForStream().createGroup(
                    STOCK_RESTORE_STREAM,
                    ReadOffset.from("0-0"),
                    STOCK_RESTORE_GROUP
            );

            log.info(
                    "Redis Stream Consumer Group 생성 완료. stream={}, group={}",
                    STOCK_RESTORE_STREAM,
                    STOCK_RESTORE_GROUP
            );
        } catch (RuntimeException e) {
            if (isGroupAlreadyExists(e)) {
                log.info(
                        "Redis Stream Consumer Group이 이미 존재합니다. stream={}, group={}",
                        STOCK_RESTORE_STREAM,
                        STOCK_RESTORE_GROUP
                );
                return;
            }

            throw e;
        }
    }

    private boolean isGroupAlreadyExists(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            String message = current.getMessage();

            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}