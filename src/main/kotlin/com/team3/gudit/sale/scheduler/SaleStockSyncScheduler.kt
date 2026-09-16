package com.team3.gudit.sale.scheduler

import com.team3.gudit.sale.domain.enums.SaleStatus
import com.team3.gudit.sale.domain.repository.SaleRepository
import com.team3.gudit.sale.service.SaleService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class SaleStockSyncScheduler(
    private val saleRepository: SaleRepository,
    private val saleService: SaleService,
) {
    @Scheduled(fixedDelay = 300_000)
    fun syncFinalRemainingStocks() {
        val finalSyncThreshold = LocalDateTime.now().minusDays(1)
        val targets =
            saleRepository.findByStatusAndFinalStockSyncedAtIsNullAndEndAtLessThanEqual(
                SaleStatus.CLOSED,
                finalSyncThreshold,
            )

        for (sale in targets) {
            try {
                val synced = saleService.syncFinalRemainingStock(sale.id)

                if (synced) {
                    log.info(
                        "[판매 최종 재고 동기화 완료] saleId={}",
                        sale.id,
                    )
                }
            } catch (exception: Exception) {
                log.error(
                    "[판매 최종 재고 동기화 실패] saleId={}",
                    sale.id,
                    exception,
                )
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SaleStockSyncScheduler::class.java)
    }
}
