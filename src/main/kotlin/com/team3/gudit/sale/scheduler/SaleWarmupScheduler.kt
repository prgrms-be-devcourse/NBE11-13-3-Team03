package com.team3.gudit.sale.scheduler

import com.team3.gudit.sale.domain.enums.SaleStatus
import com.team3.gudit.sale.domain.repository.SaleRepository
import com.team3.gudit.sale.service.InventoryService
import com.team3.gudit.sale.service.SaleService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class SaleWarmupScheduler(
    private val saleRepository: SaleRepository,
    private val inventoryService: InventoryService,
    private val saleService: SaleService,
) {
    // 30초 마다 실행 (30,000 ms)
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    fun autoWarmupSales() {
        val now = LocalDateTime.now()
        val targetTime = now.plusMinutes(10)

        // 1. 시작 10분 전 이내이고, 상태가 READY인 타임세일 조회
        val upcomingSales =
            saleRepository.findByStatusAndStartAtBetween(
                SaleStatus.READY,
                now,
                targetTime,
            )

        for (sale in upcomingSales) {
            try {
                saleService.warmupSaleInfo(sale.id)
                log.info("[자동 Warm-up 완료] saleId: {}, startAt: {}", sale.id, sale.startAt)
            } catch (exception: Exception) {
                log.error("[자동 Warm-up 실패] saleId: {}", sale.id, exception)
            }
        }
    }

    @Scheduled(fixedDelay = 60_000)
    fun startSales() {
        val now = LocalDateTime.now()
        val sales =
            saleRepository.findByStatusAndStartAtLessThanEqualAndEndAtGreaterThan(
                SaleStatus.READY,
                now,
                now,
            )

        for (sale in sales) {
            try {
                // 웜업 스케줄을 놓친 경우도 대비
                saleService.warmupSaleInfo(sale.id)
                saleService.startSale(sale.id)

                log.info(
                    "[자동 판매 시작 완료] saleId: {}, startAt: {}",
                    sale.id,
                    sale.startAt,
                )
            } catch (exception: Exception) {
                log.error(
                    "[자동 판매 시작 실패] saleId: {}",
                    sale.id,
                    exception,
                )
            }
        }
    }

    @Scheduled(fixedDelay = 60_000)
    fun endSales() {
        val now = LocalDateTime.now()
        val sales =
            saleRepository.findByStatusAndEndAtLessThanEqual(
                SaleStatus.ON_SALE,
                now,
            )

        for (sale in sales) {
            try {
                saleService.endSale(sale.id)

                log.info(
                    "[자동 판매 종료 완료] saleId: {}, endAt: {}",
                    sale.id,
                    sale.endAt,
                )
            } catch (exception: Exception) {
                log.error(
                    "[자동 판매 종료 실패] saleId: {}",
                    sale.id,
                    exception,
                )
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SaleWarmupScheduler::class.java)
    }
}
