package com.team3.gudit.performance;

import com.team3.gudit.sale.metrics.InventoryMetrics;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@Profile("performance")
@RequestMapping("/api/internal/performance-probe")
@RequiredArgsConstructor
public class PerformanceProbeController {

    private final PerformanceProbeRegistry registry;
    private final InventoryMetrics inventoryMetrics;

    @PostMapping("/{runId}/reset")
    public ResponseEntity<Void> reset(@PathVariable String runId) {
        registry.reset(runId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{runId}")
    public PerformanceProbeRegistry.Snapshot snapshot(@PathVariable String runId) {
        return registry.snapshot(runId);
    }

    @DeleteMapping("/{runId}")
    public ResponseEntity<Void> remove(@PathVariable String runId) {
        registry.remove(runId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/server-error")
    public void serverError() {
        throw new IllegalStateException(
                "Intentional server error for observability alert test"
        );
    }

    @GetMapping("/slow-response")
    public ResponseEntity<Void> slowResponse() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Slow response test was interrupted",
                    exception
            );
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/rollback-failure")
    public ResponseEntity<Void> rollbackFailure() {
        inventoryMetrics.recordRollback("failed");

        return ResponseEntity.noContent().build();
    }
}
