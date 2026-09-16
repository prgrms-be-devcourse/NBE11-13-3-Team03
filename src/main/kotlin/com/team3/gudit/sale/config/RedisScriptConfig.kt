package com.team3.gudit.sale.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.scripting.support.ResourceScriptSource

@Configuration
class RedisScriptConfig {
    @Bean
    fun stockDecrementScript(): DefaultRedisScript<Long> {
        val redisScript = DefaultRedisScript<Long>()
        redisScript.setScriptSource(
            ResourceScriptSource(
                ClassPathResource("scripts/stock_decrement.lua"),
            ),
        )
        redisScript.setResultType(Long::class.javaObjectType)
        return redisScript
    }

    @Bean
    fun stockRestoreScript(): DefaultRedisScript<Long> {
        val redisScript = DefaultRedisScript<Long>()
        redisScript.setScriptSource(
            ResourceScriptSource(
                ClassPathResource("scripts/stock_restore.lua"),
            ),
        )
        redisScript.setResultType(Long::class.javaObjectType)
        return redisScript
    }

    @Bean
    fun stockRestoreIdempotentScript(): DefaultRedisScript<Long> {
        val redisScript = DefaultRedisScript<Long>()
        redisScript.setScriptSource(
            ResourceScriptSource(
                ClassPathResource("scripts/stock_restore_idempotent.lua"),
            ),
        )
        redisScript.setResultType(Long::class.javaObjectType)
        return redisScript
    }
}
