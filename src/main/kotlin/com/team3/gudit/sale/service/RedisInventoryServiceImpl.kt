package com.team3.gudit.sale.service

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.global.exception.GlobalErrorCode
import com.team3.gudit.sale.domain.repository.SaleRepository
import com.team3.gudit.sale.exception.SaleErrorCode
import com.team3.gudit.sale.metrics.InventoryMetrics
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.Instant

@Primary
@Service
@ConditionalOnProperty(
    prefix = "gudit.inventory",
    name = ["mode"],
    havingValue = "redis",
    matchIfMissing = true,
)
class RedisInventoryServiceImpl(
    private val redisTemplate: StringRedisTemplate,
    private val stockDecrementScript: DefaultRedisScript<Long>,
    private val stockRestoreScript: DefaultRedisScript<Long>,
    private val stockRestoreIdempotentScript: DefaultRedisScript<Long>,
    private val saleRepository: SaleRepository,
    private val inventoryMetrics: InventoryMetrics,
) : InventoryService {
    override fun decreaseStock(
        saleId: Long?,
        userId: Long?,
        quantity: Int,
    ) {
        validateQuantity(quantity)

        val stockKey = "sale:$saleId:stock"
        val infoKey = "sale:$saleId:info"
        val userKey = "sale:$saleId:user:$userId"
        val nowMilli = Instant.now().toEpochMilli()

        val sample = inventoryMetrics.startLuaTimer()
        var luaResult = "failed"

        try {
            val result =
                redisTemplate.execute(
                    stockDecrementScript,
                    listOf(stockKey, infoKey, userKey),
                    quantity.toString(),
                    nowMilli.toString(),
                )

            if (result == null) {
                inventoryMetrics.recordDecrease("failed", "null_result")
                throw BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR)
            }

            if (result < 0L) {
                luaResult = "rejected"
                inventoryMetrics.recordDecrease(
                    "rejected",
                    getDecreaseFailureReason(result),
                )
                handleScriptError(result)
            }

            luaResult = "success"
            inventoryMetrics.recordDecrease("success", "none")

            if (result == 0L) {
                inventoryMetrics.recordSoldOut()
            }
        } catch (exception: BusinessException) {
            throw exception
        } catch (exception: RuntimeException) {
            inventoryMetrics.recordDecrease("failed", "redis_error")
            throw exception
        } finally {
            inventoryMetrics.recordLuaExecution(
                sample,
                "decrement",
                luaResult,
            )
        }
    }

    override fun restoreStock(
        saleId: Long?,
        userId: Long?,
        quantity: Int,
    ) {
        validateQuantity(quantity)

        val stockKey = "sale:$saleId:stock"
        val userKey = "sale:$saleId:user:$userId"

        val sample = inventoryMetrics.startLuaTimer()
        var luaResult = "failed"

        try {
            val result =
                redisTemplate.execute(
                    stockRestoreScript,
                    listOf(stockKey, userKey),
                    quantity.toString(),
                )

            if (result == null) {
                inventoryMetrics.recordRestore("failed", "null_result")
                throw BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR)
            }

            if (result < 0L) {
                inventoryMetrics.recordRestore("failed", "stock_not_found")
                handleRestoreScriptError(result)
            }

            if (result == 0L) {
                luaResult = "rejected"
                inventoryMetrics.recordRestore("rejected", "already_restored")
                return
            }

            luaResult = "success"
            inventoryMetrics.recordRestore("success", "none")
            inventoryMetrics.recordRestoredQuantity(result)
        } catch (exception: BusinessException) {
            throw exception
        } catch (exception: RuntimeException) {
            inventoryMetrics.recordRestore("failed", "redis_error")
            throw exception
        } finally {
            inventoryMetrics.recordLuaExecution(
                sample,
                "restore",
                luaResult,
            )
        }
    }

    override fun restoreStockIdempotently(
        eventId: String?,
        saleId: Long?,
        userId: Long?,
        quantity: Int,
    ) {
        validateQuantity(quantity)

        val stockKey = "sale:$saleId:stock"
        val userKey = "sale:$saleId:user:$userId"
        val processedEventKey = "stock-restore:processed:$eventId"

        val sample = inventoryMetrics.startLuaTimer()
        var luaResult = "failed"

        try {
            val result =
                redisTemplate.execute(
                    stockRestoreIdempotentScript,
                    listOf(stockKey, userKey, processedEventKey),
                    quantity.toString(),
                    PROCESSED_EVENT_TTL_SECONDS,
                )

            if (result == null) {
                inventoryMetrics.recordRestore("failed", "null_result")
                throw BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR)
            }

            if (result < 0L) {
                inventoryMetrics.recordRestore("failed", "stock_not_found")
                handleRestoreScriptError(result)
            }

            if (result == 0L) {
                luaResult = "rejected"
                inventoryMetrics.recordRestore("rejected", "already_restored")
                return
            }

            luaResult = "success"
            inventoryMetrics.recordRestore("success", "none")
            inventoryMetrics.recordRestoredQuantity(result)
        } catch (exception: BusinessException) {
            throw exception
        } catch (exception: RuntimeException) {
            inventoryMetrics.recordRestore("failed", "redis_error")
            throw exception
        } finally {
            inventoryMetrics.recordLuaExecution(
                sample,
                "restore_idempotent",
                luaResult,
            )
        }
    }

    private fun getDecreaseFailureReason(errorCode: Long): String =
        when (errorCode) {
            -1L -> "not_enough_stock"
            -2L -> "invalid_sale_period"
            -3L -> "exceeded_purchase_quantity"
            -4L -> "sale_closed"
            else -> "unknown"
        }

    // 비즈니스 에러 처리 (-1: 재고부족, -2: 기간아님, -3: 수량초과, -4: 종료)
    private fun handleScriptError(errorCode: Long): Nothing =
        when (errorCode) {
            -1L -> throw BusinessException(SaleErrorCode.NOT_ENOUGH_STOCK)
            -2L -> throw BusinessException(SaleErrorCode.INVALID_SALE_PERIOD)
            -3L -> throw BusinessException(SaleErrorCode.EXCEEDED_PURCHASE_QUANTITY)
            -4L -> throw BusinessException(SaleErrorCode.SALE_CLOSED)
            else -> throw BusinessException(SaleErrorCode.NOT_ENOUGH_STOCK)
        }

    private fun handleRestoreScriptError(errorCode: Long): Nothing {
        if (errorCode == -1L) {
            throw BusinessException(SaleErrorCode.REDIS_STOCK_NOT_FOUND)
        }

        throw BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR)
    }

    private fun validateQuantity(quantity: Int) {
        if (quantity <= 0) {
            throw BusinessException(SaleErrorCode.INVALID_PURCHASE_QUANTITY)
        }
    }

    companion object {
        private const val PROCESSED_EVENT_TTL_SECONDS = "86400"
    }
}
