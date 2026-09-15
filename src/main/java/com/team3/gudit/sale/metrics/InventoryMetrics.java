package com.team3.gudit.sale.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class InventoryMetrics {

    private static final String DECREASE_METRIC =
            "gudit.inventory.stock.decrease";

    private static final String SOLD_OUT_METRIC =
            "gudit.inventory.stock.soldout";

    private static final String RESTORE_METRIC =
            "gudit.inventory.stock.restore";

    private static final String LUA_EXECUTION_METRIC =
            "gudit.inventory.lua.execution";

    private static final String RESTORED_QUANTITY_METRIC =
            "gudit.inventory.stock.restored.quantity";

    private static final String ROLLBACK_METRIC =
            "gudit.inventory.stock.rollback";

    private final MeterRegistry meterRegistry;

    public InventoryMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        registerDecreaseCounters();
        registerRestoreCounters();

        meterRegistry.counter(SOLD_OUT_METRIC);
        meterRegistry.counter(RESTORED_QUANTITY_METRIC);
        meterRegistry.counter(
                ROLLBACK_METRIC,
                "result", "success"
        );

        meterRegistry.counter(
                ROLLBACK_METRIC,
                "result", "failed"
        );
    }

    public void recordDecrease(String result, String reason) {
        meterRegistry.counter(
                DECREASE_METRIC,
                "result", result,
                "reason", reason
        ).increment();
    }

    public void recordSoldOut() {
        meterRegistry.counter(SOLD_OUT_METRIC).increment();
    }

    public void recordRestore(String result, String reason) {
        meterRegistry.counter(
                RESTORE_METRIC,
                "result", result,
                "reason", reason
        ).increment();
    }

    public Timer.Sample startLuaTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordLuaExecution(
            Timer.Sample sample,
            String script,
            String result
    ) {
        sample.stop(
                Timer.builder(LUA_EXECUTION_METRIC)
                        .description("Redis inventory Lua script execution time")
                        .tag("script", script)
                        .tag("result", result)
                        .publishPercentileHistogram()
                        .register(meterRegistry)
        );
    }

    public void recordRestoredQuantity(long quantity) {
        meterRegistry.counter(
                RESTORED_QUANTITY_METRIC
        ).increment(quantity);
    }

    public void recordRollback(String result) {
        meterRegistry.counter(
                ROLLBACK_METRIC,
                "result", result
        ).increment();
    }

    private void registerDecreaseCounters() {
        meterRegistry.counter(
                DECREASE_METRIC,
                "result", "success",
                "reason", "none"
        );

        registerDecreaseRejected("not_enough_stock");
        registerDecreaseRejected("invalid_sale_period");
        registerDecreaseRejected("exceeded_purchase_quantity");
        registerDecreaseRejected("sale_closed");
        registerDecreaseRejected("unknown");

        registerDecreaseFailed("null_result");
        registerDecreaseFailed("redis_error");
    }

    private void registerDecreaseRejected(String reason) {
        meterRegistry.counter(
                DECREASE_METRIC,
                "result", "rejected",
                "reason", reason
        );
    }

    private void registerDecreaseFailed(String reason) {
        meterRegistry.counter(
                DECREASE_METRIC,
                "result", "failed",
                "reason", reason
        );
    }

    private void registerRestoreCounters() {
        meterRegistry.counter(
                RESTORE_METRIC,
                "result", "success",
                "reason", "none"
        );

        meterRegistry.counter(
                RESTORE_METRIC,
                "result", "rejected",
                "reason", "already_restored"
        );

        registerRestoreFailed("stock_not_found");
        registerRestoreFailed("null_result");
        registerRestoreFailed("redis_error");
    }

    private void registerRestoreFailed(String reason) {
        meterRegistry.counter(
                RESTORE_METRIC,
                "result", "failed",
                "reason", reason
        );
    }
}