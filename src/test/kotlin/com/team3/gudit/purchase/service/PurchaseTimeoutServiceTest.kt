package com.team3.gudit.purchase.service

import com.team3.gudit.outbox.service.OutboxEventService
import com.team3.gudit.payment.entity.Payment
import com.team3.gudit.payment.entity.PaymentStatus
import com.team3.gudit.payment.repository.PaymentRepository
import com.team3.gudit.purchase.entity.Purchase
import com.team3.gudit.purchase.entity.PurchaseStatus
import com.team3.gudit.purchase.repository.PurchaseRepository
import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.user.domain.entity.User
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PurchaseTimeoutServiceTest {

    @Mock
    private lateinit var purchaseRepository: PurchaseRepository

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var outboxEventService: OutboxEventService

    private lateinit var purchaseTimeoutService: PurchaseTimeoutService

    @BeforeEach
    fun setUp() {
        purchaseTimeoutService = PurchaseTimeoutService(purchaseRepository, paymentRepository, outboxEventService)
    }

    @Test
    @DisplayName("PENDING_PAYMENT Purchase와 READY Payment를 " + "timeout 취소하고 재고 복구 Outbox 이벤트를 저장한다")
    fun cancelExpiredPurchase() {
        // given
        val purchaseId = 100L
        val saleId = 10L
        val userId = 1L
        val quantity = 2

        val lockedPurchase = mock(Purchase::class.java)
        val sale = mock(Sale::class.java)
        val user = mock(User::class.java)

        given(lockedPurchase.id)
            .willReturn(purchaseId)

        given(lockedPurchase.status)
            .willReturn(PurchaseStatus.PENDING_PAYMENT)

        given(lockedPurchase.sale)
            .willReturn(sale)

        given(lockedPurchase.user)
            .willReturn(user)

        given(lockedPurchase.quantity)
            .willReturn(quantity)

        given(sale.id)
            .willReturn(saleId)

        given(user.id)
            .willReturn(userId)

        val payment = Payment.create(lockedPurchase, 20_000)

        given(purchaseRepository.findByIdWithLock(purchaseId))
            .willReturn(Optional.of(lockedPurchase))

        given(paymentRepository.findByPurchaseId(purchaseId))
            .willReturn(Optional.of(payment))

        // when
        val canceled = purchaseTimeoutService
            .cancelExpiredPurchase(purchaseId)

        // then
        assertThat(canceled).isTrue()

        assertThat(payment.status)
            .isEqualTo(PaymentStatus.CANCELED)

        verify(outboxEventService)
            .saveStockRestoreRequested(purchaseId, saleId, userId, quantity)

        verify(lockedPurchase).cancel()
    }

    @Test
    @DisplayName("Purchase가 이미 처리된 상태이면 " + "timeout 취소와 재고 복구 이벤트 저장을 하지 않는다")
    fun skipAlreadyProcessedPurchase() {
        // given
        val purchaseId = 100L

        val lockedPurchase = mock(Purchase::class.java)

        given(lockedPurchase.id)
            .willReturn(purchaseId)

        given(lockedPurchase.status)
            .willReturn(PurchaseStatus.CANCELED)

        given(purchaseRepository.findByIdWithLock(purchaseId))
            .willReturn(Optional.of(lockedPurchase))

        // when
        val canceled = purchaseTimeoutService
            .cancelExpiredPurchase(purchaseId)

        // then
        assertThat(canceled).isFalse()

        verifyNoInteractions(paymentRepository)
        verifyNoInteractions(outboxEventService)
        verify(lockedPurchase, never()).cancel()
    }

    @Test
    @DisplayName("Payment가 IN_PROGRESS이면 " + "결제 승인 중이므로 timeout 처리에서 제외한다")
    fun skipInProgressPayment() {
        // given
        val purchaseId = 100L

        val lockedPurchase = mock(Purchase::class.java)

        given(lockedPurchase.id)
            .willReturn(purchaseId)

        given(lockedPurchase.status)
            .willReturn(PurchaseStatus.PENDING_PAYMENT)

        val payment = Payment.create(lockedPurchase, 20_000)

        payment.start("test-payment-key")

        given(purchaseRepository.findByIdWithLock(purchaseId))
            .willReturn(Optional.of(lockedPurchase))

        given(paymentRepository.findByPurchaseId(purchaseId))
            .willReturn(Optional.of(payment))

        // when
        val canceled = purchaseTimeoutService
            .cancelExpiredPurchase(purchaseId)

        // then
        assertThat(canceled).isFalse()

        assertThat(payment.status)
            .isEqualTo(PaymentStatus.IN_PROGRESS)

        verifyNoInteractions(outboxEventService)
        verify(lockedPurchase, never()).cancel()
    }

    @Test
    @DisplayName("Payment가 없으면 Purchase와 재고 복구 이벤트를 " + "변경하지 않고 timeout 처리를 보류한다")
    fun skipWhenPaymentDoesNotExist() {
        // given
        val purchaseId = 100L

        val lockedPurchase = mock(Purchase::class.java)

        given(lockedPurchase.id)
            .willReturn(purchaseId)

        given(lockedPurchase.status)
            .willReturn(PurchaseStatus.PENDING_PAYMENT)

        given(purchaseRepository.findByIdWithLock(purchaseId))
            .willReturn(Optional.of(lockedPurchase))

        given(paymentRepository.findByPurchaseId(purchaseId))
            .willReturn(Optional.empty())

        // when
        val canceled = purchaseTimeoutService
            .cancelExpiredPurchase(purchaseId)

        // then
        assertThat(canceled).isFalse()

        verifyNoInteractions(outboxEventService)
        verify(lockedPurchase, never()).cancel()
    }

    @Test
    @DisplayName("Outbox 저장에서 예외가 발생하면 예외를 전파한다")
    fun propagateOutboxSaveFailure() {
        // given
        val purchaseId = 100L
        val saleId = 10L
        val userId = 1L
        val quantity = 2

        val lockedPurchase = mock(Purchase::class.java)
        val sale = mock(Sale::class.java)
        val user = mock(User::class.java)

        given(lockedPurchase.id)
            .willReturn(purchaseId)

        given(lockedPurchase.status)
            .willReturn(PurchaseStatus.PENDING_PAYMENT)

        given(lockedPurchase.sale)
            .willReturn(sale)

        given(lockedPurchase.user)
            .willReturn(user)

        given(lockedPurchase.quantity)
            .willReturn(quantity)

        given(sale.id)
            .willReturn(saleId)

        given(user.id)
            .willReturn(userId)

        val payment = Payment.create(lockedPurchase, 20_000)

        given(purchaseRepository.findByIdWithLock(purchaseId))
            .willReturn(Optional.of(lockedPurchase))

        given(paymentRepository.findByPurchaseId(purchaseId))
            .willReturn(Optional.of(payment))

        doThrow(RuntimeException("Outbox 저장 실패"))
            .`when`(outboxEventService)
            .saveStockRestoreRequested(purchaseId, saleId, userId, quantity)

        // when & then
        assertThatThrownBy {
            purchaseTimeoutService
                .cancelExpiredPurchase(purchaseId)
        }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("Outbox 저장 실패")

        verify(outboxEventService)
            .saveStockRestoreRequested(purchaseId, saleId, userId, quantity)
    }

    @Test
    @DisplayName("Purchase를 찾을 수 없으면 " + "timeout 처리를 하지 않는다")
    fun skipWhenPurchaseDoesNotExist() {
        // given
        val purchaseId = 100L

        given(purchaseRepository.findByIdWithLock(purchaseId))
            .willReturn(Optional.empty())

        // when
        val canceled = purchaseTimeoutService
            .cancelExpiredPurchase(purchaseId)

        // then
        assertThat(canceled).isFalse()

        verifyNoInteractions(paymentRepository)
        verifyNoInteractions(outboxEventService)
    }
}
