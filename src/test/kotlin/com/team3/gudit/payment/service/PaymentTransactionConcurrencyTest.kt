package com.team3.gudit.payment.service

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.outbox.service.OutboxEventService
import com.team3.gudit.payment.dto.TossPaymentResponse
import com.team3.gudit.payment.entity.Payment
import com.team3.gudit.payment.entity.PaymentStatus
import com.team3.gudit.payment.repository.PaymentRepository
import com.team3.gudit.purchase.entity.Purchase
import com.team3.gudit.purchase.entity.PurchaseStatus
import com.team3.gudit.purchase.exception.PurchaseErrorCode
import com.team3.gudit.purchase.repository.PurchaseRepository
import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.user.domain.entity.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

import java.time.OffsetDateTime
import java.util.Optional

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.BDDMockito.given
import org.mockito.Mockito.*

@ExtendWith(MockitoExtension::class)
class PaymentTransactionConcurrencyTest {

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var purchaseRepository: PurchaseRepository

    @Mock
    private lateinit var outboxEventService: OutboxEventService

    private lateinit var paymentTransactionService: PaymentTransactionService

    private var purchaseId: Long = 0
    private var saleId: Long = 0
    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        paymentTransactionService =
                PaymentTransactionService(
                        paymentRepository,
                        purchaseRepository,
                        outboxEventService
                )

        purchaseId = 100L
        saleId = 10L
        userId = 1L
    }

    @Test
    @DisplayName("결제 시작 전에 Purchase를 잠금 조회하고 PENDING_PAYMENT 상태를 재검증한다")
    fun startPaymentWithLockedPurchase() {
        // given
        val amount = 15_000
        val paymentKey = "payment-key"

        val paymentPurchase = mock(Purchase::class.java)
        val lockedPurchase = mock(Purchase::class.java)

        given(paymentPurchase.id)
                .willReturn(purchaseId)
        given(paymentPurchase.purchasePrice)
                .willReturn(amount)

        given(lockedPurchase.status)
                .willReturn(PurchaseStatus.PENDING_PAYMENT)

        val payment = Payment.create(
                paymentPurchase,
                amount
        )

        given(paymentRepository.findByOrderIdWithLock(
                payment.orderId
        )).willReturn(Optional.of(payment))

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(lockedPurchase))

        // when
        paymentTransactionService.startPayment(
                payment.orderId,
                paymentKey,
                amount
        )

        // then
        verify(paymentRepository)
                .findByOrderIdWithLock(
                        payment.orderId
                )

        verify(purchaseRepository)
                .findByIdWithLock(purchaseId)

        assertThat(payment.status)
                .isEqualTo(PaymentStatus.IN_PROGRESS)

        assertThat(payment.paymentKey)
                .isEqualTo(paymentKey)
    }

    @Test
    @DisplayName("잠금 조회한 Purchase가 CANCELED이면 결제를 시작할 수 없다")
    fun startPaymentWhenPurchaseCanceled() {
        // given
        val amount = 15_000

        val paymentPurchase = mock(Purchase::class.java)
        val lockedPurchase = mock(Purchase::class.java)

        given(paymentPurchase.id)
                .willReturn(purchaseId)
        given(paymentPurchase.purchasePrice)
                .willReturn(amount)

        given(lockedPurchase.status)
                .willReturn(PurchaseStatus.CANCELED)

        val payment = Payment.create(
                paymentPurchase,
                amount
        )

        given(paymentRepository.findByOrderIdWithLock(
                payment.orderId
        )).willReturn(Optional.of(payment))

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(lockedPurchase))

        // when & then
        assertThatThrownBy {
            paymentTransactionService.startPayment(
                        payment.orderId,
                        "payment-key",
                        amount
                )
        }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(java.util.function.Consumer<Throwable> { exception ->
                    assertThat(
                                (exception as BusinessException)
                                        .errorCode
                        ).isEqualTo(
                                PurchaseErrorCode
                                        .INVALID_PURCHASE_STATUS
                        ) })

        assertThat(payment.status)
                .isEqualTo(PaymentStatus.READY)
    }

    @Test
    @DisplayName("결제 완료 시 잠금 조회한 Purchase를 완료 처리한다")
    fun completePaymentWithLockedPurchase() {
        // given
        val amount = 15_000

        val paymentPurchase = mock(Purchase::class.java)
        val lockedPurchase = mock(Purchase::class.java)
        val response =
                mock(TossPaymentResponse::class.java)

        given(paymentPurchase.id)
                .willReturn(purchaseId)

        given(lockedPurchase.status)
                .willReturn(PurchaseStatus.PENDING_PAYMENT)

        val payment = Payment.create(
                paymentPurchase,
                amount
        )

        payment.start("payment-key")

        given(paymentRepository.findByOrderIdWithLock(
                payment.orderId
        )).willReturn(Optional.of(payment))

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(lockedPurchase))

        given(response.orderId)
                .willReturn(payment.orderId)

        given(response.totalAmount)
                .willReturn(amount)

        given(response.approvedAt)
                .willReturn(
                        OffsetDateTime.parse(
                                "2026-08-19T10:00:00+09:00"
                        )
                )

        // when
        paymentTransactionService.completePayment(
                payment.orderId,
                response
        )

        // then
        verify(paymentRepository)
                .findByOrderIdWithLock(
                        payment.orderId
                )

        verify(purchaseRepository)
                .findByIdWithLock(purchaseId)

        verify(lockedPurchase).complete()

        verify(paymentPurchase, never()).complete()

        assertThat(payment.status)
                .isEqualTo(PaymentStatus.DONE)
    }

    @Test
    @DisplayName("결제 실패 시 PENDING_PAYMENT Purchase만 취소하고 재고 복구 Outbox 이벤트를 저장한다")
    fun failPaymentWithPendingPurchase() {
        // given
        val paymentPurchase = mock(Purchase::class.java)
        val lockedPurchase = mock(Purchase::class.java)
        val sale = mock(Sale::class.java)
        val user = mock(User::class.java)

        given(paymentPurchase.id)
                .willReturn(purchaseId)

        given(lockedPurchase.id)
                .willReturn(purchaseId)

        given(lockedPurchase.status)
                .willReturn(PurchaseStatus.PENDING_PAYMENT)

        given(lockedPurchase.sale)
                .willReturn(sale)

        given(lockedPurchase.user)
                .willReturn(user)

        given(lockedPurchase.quantity)
                .willReturn(1)

        given(sale.id)
                .willReturn(saleId)

        given(user.id)
                .willReturn(userId)

        val payment = Payment.create(
                paymentPurchase,
                15_000
        )

        payment.start("payment-key")

        given(paymentRepository.findByOrderId(
                payment.orderId
        )).willReturn(Optional.of(payment))

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(lockedPurchase))

        // when
        paymentTransactionService.failPayment(
                payment.orderId
        )

        // then
        assertThat(payment.status)
                .isEqualTo(PaymentStatus.FAILED)

        verify(outboxEventService)
                .saveStockRestoreRequested(
                        purchaseId,
                        saleId,
                        userId,
                        1
                )

        verify(lockedPurchase).cancel()
        verify(paymentPurchase, never()).cancel()
    }

    @Test
    @DisplayName("결제 실패 시 Purchase가 이미 CANCELED이면 재고 복구 Outbox 이벤트를 중복 저장하지 않는다")
    fun failPaymentWhenPurchaseAlreadyCanceled() {
        // given
        val paymentPurchase = mock(Purchase::class.java)
        val lockedPurchase = mock(Purchase::class.java)

        given(paymentPurchase.id)
                .willReturn(purchaseId)

        given(lockedPurchase.status)
                .willReturn(PurchaseStatus.CANCELED)

        val payment = Payment.create(
                paymentPurchase,
                15_000
        )

        payment.start("payment-key")

        given(paymentRepository.findByOrderId(
                payment.orderId
        )).willReturn(Optional.of(payment))

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(lockedPurchase))

        // when
        paymentTransactionService.failPayment(
                payment.orderId
        )

        // then
        assertThat(payment.status)
                .isEqualTo(PaymentStatus.FAILED)

        verify(outboxEventService, never())
                .saveStockRestoreRequested(
                        anyLong(),
                        anyLong(),
                        anyLong(),
                        anyInt()
                )

        verify(lockedPurchase, never()).cancel()
    }

    @Test
    @DisplayName("승인 실패 보상 시 Purchase가 이미 CANCELED이면 재고 복구 Outbox 이벤트를 중복 저장하지 않는다")
    fun compensateApprovalFailureWhenPurchaseAlreadyCanceled() {
        // given
        val paymentPurchase = mock(Purchase::class.java)
        val lockedPurchase = mock(Purchase::class.java)

        given(paymentPurchase.id)
                .willReturn(purchaseId)

        given(lockedPurchase.status)
                .willReturn(PurchaseStatus.CANCELED)

        val payment = Payment.create(
                paymentPurchase,
                15_000
        )

        payment.start("payment-key")

        given(paymentRepository.findByPaymentKey(
                "payment-key"
        )).willReturn(Optional.of(payment))

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(lockedPurchase))

        // when
        paymentTransactionService
                .compensateApprovalFailure(
                        "payment-key"
                )

        // then
        assertThat(payment.status)
                .isEqualTo(PaymentStatus.CANCELED)

        verify(outboxEventService, never())
                .saveStockRestoreRequested(
                        anyLong(),
                        anyLong(),
                        anyLong(),
                        anyInt()
                )

        verify(lockedPurchase, never()).cancel()
    }
}