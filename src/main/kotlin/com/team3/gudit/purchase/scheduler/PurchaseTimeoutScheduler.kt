package com.team3.gudit.purchase.scheduler

import com.team3.gudit.purchase.entity.Purchase
import com.team3.gudit.purchase.entity.PurchaseStatus
import com.team3.gudit.purchase.repository.PurchaseRepository
import com.team3.gudit.purchase.service.PurchaseTimeoutService
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PurchaseTimeoutScheduler(
    private val purchaseRepository: PurchaseRepository,
    private val purchaseTimeoutService: PurchaseTimeoutService
) {
    private val log = LoggerFactory.getLogger(PurchaseTimeoutScheduler::class.java)

    // 1분마다 실행
    @Scheduled(cron = "0 * * * * *")
    fun cancelExpiredPurchases() {
        // 현재 시각으로부터 10분 전에 생성된 미결제 구매 조회
        val threshold = LocalDateTime.now().minusMinutes(10)

        val expiredPurchases = purchaseRepository
            .findAllByStatusAndCreatedAtBefore(PurchaseStatus.PENDING_PAYMENT, threshold)

        if (expiredPurchases.isEmpty()) {
            return
        }

        log.info(
            "미결제 timeout 처리 대상: {}건",
            expiredPurchases.size
        )

        for (purchase in expiredPurchases) {
            try {
                // 별도 Bean의 @Transactional 메서드를 호출하므로
                // Purchase 한 건마다 독립된 트랜잭션이 생성된다.
                val canceled = purchaseTimeoutService
                    .cancelExpiredPurchase(purchase.id)

                if (canceled) {
                    log.info(
                        "미결제 timeout 처리 완료: purchaseId={}",
                        purchase.id
                    )
                }
            } catch (e: Exception) {
                // 개별 트랜잭션은 이미 롤백된 상태이며, 다음 timeout 대상 처리를 계속한다.
                log.error(
                    "미결제 timeout 처리 중 예외 발생: "
                    + "purchaseId={}",
                    purchase.id,
                    e
                )
            }
        }
    }
}
