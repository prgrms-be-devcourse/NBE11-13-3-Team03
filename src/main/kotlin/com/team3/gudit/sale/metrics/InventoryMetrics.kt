package com.team3.gudit.sale.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component

@Component
class InventoryMetrics(
    private val meterRegistry: MeterRegistry,
) {
    init {
        registerDecreaseCounters()
        registerRestoreCounters()

        meterRegistry.counter(SOLD_OUT_METRIC)
        meterRegistry.counter(RESTORED_QUANTITY_METRIC)
        meterRegistry.counter(
            ROLLBACK_METRIC,
            "result",
            "success",
        )
        meterRegistry.counter(
            ROLLBACK_METRIC,
            "result",
            "failed",
        )
    }

    fun recordDecrease(
        result: String,
        reason: String,
    ) {
        meterRegistry.counter(
            DECREASE_METRIC,
            "result",
            result,
            "reason",
            reason,
        ).increment()
    }

    fun recordSoldOut() {
        meterRegistry.counter(SOLD_OUT_METRIC).increment()
    }

    fun recordRestore(
        result: String,
        reason: String,
    ) {
        meterRegistry.counter(
            RESTORE_METRIC,
            "result",
            result,
            "reason",
            reason,
        ).increment()
    }

    fun startLuaTimer(): Timer.Sample = Timer.start(meterRegistry)

    fun recordLuaExecution(
        sample: Timer.Sample,
        script: String,
        result: String,
    ) {
        sample.stop(
            Timer.builder(LUA_EXECUTION_METRIC)
                .description("Redis inventory Lua script execution time")
                .tag("script", script)
                .tag("result", result)
                .publishPercentileHistogram()
                .register(meterRegistry),
        )
    }

    fun recordRestoredQuantity(quantity: Long) {
        meterRegistry.counter(RESTORED_QUANTITY_METRIC).increment(quantity.toDouble())
    }

    fun recordRollback(result: String) {
        meterRegistry.counter(
            ROLLBACK_METRIC,
            "result",
            result,
        ).increment()
    }

    private fun registerDecreaseCounters() {
        meterRegistry.counter(
            DECREASE_METRIC,
            "result",
            "success",
            "reason",
            "none",
        )

        registerDecreaseRejected("not_enough_stock")
        registerDecreaseRejected("invalid_sale_period")
        registerDecreaseRejected("exceeded_purchase_quantity")
        registerDecreaseRejected("sale_closed")
        registerDecreaseRejected("unknown")

        registerDecreaseFailed("null_result")
        registerDecreaseFailed("redis_error")
    }

    private fun registerDecreaseRejected(reason: String) {
        meterRegistry.counter(
            DECREASE_METRIC,
            "result",
            "rejected",
            "reason",
            reason,
        )
    }

    private fun registerDecreaseFailed(reason: String) {
        meterRegistry.counter(
            DECREASE_METRIC,
            "result",
            "failed",
            "reason",
            reason,
        )
    }

    private fun registerRestoreCounters() {
        meterRegistry.counter(
            RESTORE_METRIC,
            "result",
            "success",
            "reason",
            "none",
        )
        meterRegistry.counter(
            RESTORE_METRIC,
            "result",
            "rejected",
            "reason",
            "already_restored",
        )

        registerRestoreFailed("stock_not_found")
        registerRestoreFailed("null_result")
        registerRestoreFailed("redis_error")
    }

    private fun registerRestoreFailed(reason: String) {
        meterRegistry.counter(
            RESTORE_METRIC,
            "result",
            "failed",
            "reason",
            reason,
        )
    }

    companion object {
        private const val DECREASE_METRIC = "gudit.inventory.stock.decrease"
        private const val SOLD_OUT_METRIC = "gudit.inventory.stock.soldout"
        private const val RESTORE_METRIC = "gudit.inventory.stock.restore"
        private const val LUA_EXECUTION_METRIC = "gudit.inventory.lua.execution"
        private const val RESTORED_QUANTITY_METRIC = "gudit.inventory.stock.restored.quantity"
        private const val ROLLBACK_METRIC = "gudit.inventory.stock.rollback"
    }
}
