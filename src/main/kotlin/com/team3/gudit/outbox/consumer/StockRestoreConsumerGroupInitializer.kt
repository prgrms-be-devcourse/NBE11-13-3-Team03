package com.team3.gudit.outbox.consumer

import com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.STOCK_RESTORE_GROUP
import com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.STOCK_RESTORE_STREAM
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class StockRestoreConsumerGroupInitializer(
    private val stringRedisTemplate: StringRedisTemplate
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        try {
            stringRedisTemplate.opsForStream<String, String>().createGroup(
                STOCK_RESTORE_STREAM,
                ReadOffset.from("0-0"),
                STOCK_RESTORE_GROUP
            )

            log.info(
                "Redis Stream Consumer Group 생성 완료. stream={}, group={}",
                STOCK_RESTORE_STREAM,
                STOCK_RESTORE_GROUP
            )
        } catch (e: RuntimeException) {
            if (isGroupAlreadyExists(e)) {
                log.info(
                    "Redis Stream Consumer Group이 이미 존재합니다. stream={}, group={}",
                    STOCK_RESTORE_STREAM,
                    STOCK_RESTORE_GROUP
                )
                return
            }

            throw e
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