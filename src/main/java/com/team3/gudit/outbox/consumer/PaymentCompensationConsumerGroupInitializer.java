package com.team3.gudit.outbox.consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import static com.team3.gudit.outbox.publisher.PaymentCompensationStreamConstants.*;

@Component
@RequiredArgsConstructor
public class PaymentCompensationConsumerGroupInitializer
        implements ApplicationRunner {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            stringRedisTemplate.opsForStream()
                    .createGroup(
                            PAYMENT_COMPENSATION_STREAM,
                            ReadOffset.from("0-0"),
                            PAYMENT_COMPENSATION_GROUP
                    );

        } catch (RuntimeException e) {
            if (!isGroupAlreadyExists(e)) {
                throw e;
            }
        }
    }

    private boolean isGroupAlreadyExists(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            String message = current.getMessage();

            if (message != null
                    && message.contains("BUSYGROUP")) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}