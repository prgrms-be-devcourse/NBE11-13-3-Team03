package com.team3.gudit.purchase.scheduler

import com.team3.gudit.purchase.entity.Purchase
import com.team3.gudit.purchase.entity.PurchaseStatus
import com.team3.gudit.purchase.repository.PurchaseRepository
import com.team3.gudit.purchase.service.PurchaseTimeoutService
import java.time.LocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PurchaseTimeoutSchedulerTest {

    @Mock
    private lateinit var purchaseRepository: PurchaseRepository

    @Mock
    private lateinit var purchaseTimeoutService: PurchaseTimeoutService

    private lateinit var scheduler: PurchaseTimeoutScheduler

    @BeforeEach
    fun setUp() {
        scheduler = PurchaseTimeoutScheduler(purchaseRepository, purchaseTimeoutService)
    }

    @Test
    @DisplayName("timeout 대상 Purchase ID를 건별 처리 서비스에 전달한다")
    fun delegatesExpiredPurchases() {
        // given
        val firstPurchase = mock(Purchase::class.java)
        val secondPurchase = mock(Purchase::class.java)

        given(firstPurchase.id)
            .willReturn(100L)

        given(secondPurchase.id)
            .willReturn(200L)

        given(purchaseRepository
                .findAllByStatusAndCreatedAtBefore(
                    eq(PurchaseStatus.PENDING_PAYMENT),
                    any(LocalDateTime::class.java)
        ))
            .willReturn(
                listOf(firstPurchase, secondPurchase)
            )

        given(purchaseTimeoutService
                .cancelExpiredPurchase(100L))
            .willReturn(true)

        given(purchaseTimeoutService
                .cancelExpiredPurchase(200L))
            .willReturn(true)

        // when
        scheduler.cancelExpiredPurchases()

        // then
        verify(purchaseTimeoutService)
            .cancelExpiredPurchase(100L)

        verify(purchaseTimeoutService)
            .cancelExpiredPurchase(200L)
    }

    @Test
    @DisplayName("한 건의 timeout 처리에서 예외가 발생해도 다음 대상을 계속 처리한다")
    fun continuesAfterIndividualFailure() {
        // given
        val failedPurchase = mock(Purchase::class.java)
        val nextPurchase = mock(Purchase::class.java)

        given(failedPurchase.id)
            .willReturn(100L)

        given(nextPurchase.id)
            .willReturn(200L)

        given(purchaseRepository
                .findAllByStatusAndCreatedAtBefore(
                    eq(PurchaseStatus.PENDING_PAYMENT),
                    any(LocalDateTime::class.java)
        ))
            .willReturn(
                listOf(failedPurchase, nextPurchase)
            )

        given(purchaseTimeoutService
                .cancelExpiredPurchase(100L))
            .willThrow(
                RuntimeException("Redis 재고 복구 실패")
            )

        given(purchaseTimeoutService
                .cancelExpiredPurchase(200L))
            .willReturn(true)

        // when
        scheduler.cancelExpiredPurchases()

        // then
        verify(purchaseTimeoutService)
            .cancelExpiredPurchase(100L)

        // 첫 번째 건이 실패해도 다음 건을 계속 처리해야 한다.
        verify(purchaseTimeoutService)
            .cancelExpiredPurchase(200L)
    }

    @Test
    @DisplayName("timeout 대상이 없으면 건별 처리 서비스를 호출하지 않는다")
    fun noExpiredPurchase() {
        // given
        given(purchaseRepository
                .findAllByStatusAndCreatedAtBefore(
                    eq(PurchaseStatus.PENDING_PAYMENT),
                    any(LocalDateTime::class.java)
        ))
            .willReturn(listOf())

        // when
        scheduler.cancelExpiredPurchases()

        // then
        verifyNoInteractions(purchaseTimeoutService)
    }
}
