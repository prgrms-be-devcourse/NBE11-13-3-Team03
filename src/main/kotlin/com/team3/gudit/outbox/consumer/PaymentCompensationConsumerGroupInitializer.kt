package com.team3.gudit.outbox.consumer

import com.team3.gudit.outbox.publisher.PaymentCompensationStreamConstants.PAYMENT_COMPENSATION_GROUP
import com.team3.gudit.outbox.publisher.PaymentCompensationStreamConstants.PAYMENT_COMPENSATION_STREAM
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class PaymentCompensationConsumerGroupInitializer(
    private val stringRedisTemplate: StringRedisTemplate
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        try {
            stringRedisTemplate.opsForStream<String, String>()
                .createGroup(
                    PAYMENT_COMPENSATION_STREAM,
                    ReadOffset.from("0-0"),
                    PAYMENT_COMPENSATION_GROUP
                )
        } catch (e: RuntimeException) {
            if (!isGroupAlreadyExists(e)) {
                throw e
            }
        }
    }

    private fun isGroupAlreadyExists(throwable: Throwable): Boolean {
        var current: Throwable? = throwable

        while (current != null) {
            val message = current.message

            if (message?.contains("BUSYGROUP") == true) {
                return true
            }

            current = current.cause
        }

        return false
    }
}