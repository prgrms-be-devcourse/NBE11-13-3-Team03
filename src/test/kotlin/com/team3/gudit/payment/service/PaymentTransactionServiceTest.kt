package com.team3.gudit.payment.service

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.outbox.service.OutboxEventService
import com.team3.gudit.payment.dto.TossPaymentResponse
import com.team3.gudit.payment.entity.Payment
import com.team3.gudit.payment.entity.PaymentStatus
import com.team3.gudit.payment.exception.PaymentErrorCode
import com.team3.gudit.payment.repository.PaymentRepository
import com.team3.gudit.purchase.entity.Purchase
import com.team3.gudit.purchase.entity.PurchaseStatus
import com.team3.gudit.purchase.repository.PurchaseRepository
import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.user.domain.entity.User
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

import java.time.OffsetDateTime
import java.util.Optional

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.BDDMockito.given
import org.mockito.Mockito.*

@ExtendWith(MockitoExtension::class)
class PaymentTransactionServiceTest {

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var outboxEventService: OutboxEventService

    @Mock
    private lateinit var purchaseRepository: PurchaseRepository

    @InjectMocks
    private lateinit var paymentTransactionService: PaymentTransactionService

    @Test
    @DisplayName("결제를 시작하면 잠근 구매 상태와 금액을 검증하고 IN_PROGRESS로 변경한다")
    fun startPayment() {
        // given
        val orderId = "GUDIT_test-order-id"
        val paymentKey = "payment-key"
        val amount = 15_000
        val purchaseId = 100L

        val purchase = mock(Purchase::class.java)
        val payment = Payment.create(
                purchase,
                amount
        )

        given(purchase.id)
                .willReturn(purchaseId)

        given(purchase.purchasePrice)
                .willReturn(amount)

        given(purchase.status)
                .willReturn(PurchaseStatus.PENDING_PAYMENT)

        given(paymentRepository.findByOrderIdWithLock(orderId))
                .willReturn(Optional.of(payment))

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(purchase))

        // when
        paymentTransactionService.startPayment(
                orderId,
                paymentKey,
                amount
        )

        // then
        assertThat(payment.status)
                .isEqualTo(PaymentStatus.IN_PROGRESS)

        assertThat(payment.paymentKey)
                .isEqualTo(paymentKey)

        verify(paymentRepository)
                .findByOrderIdWithLock(orderId)

        verify(purchaseRepository)
                .findByIdWithLock(purchaseId)
    }

    @Test
    @DisplayName("결제 금액과 요청 금액이 다르면 예외가 발생한다")
    fun startPaymentAmountMismatch() {
        // given
        val orderId = "GUDIT_test-order-id"

        val purchase = mock(Purchase::class.java)
        val payment = Payment.create(
                purchase,
                15000
        )

        given(paymentRepository.findByOrderIdWithLock(orderId))
                .willReturn(Optional.of(payment))

        // when & then
        assertThatThrownBy {
            paymentTransactionService.startPayment(
                        orderId,
                        "payment-key",
                        20000
                )
        }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(java.util.function.Consumer<Throwable> { exception ->
                    val businessException =
                            exception as BusinessException

                    assertThat(businessException.errorCode)
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH
                            ) })
    }

    @Test
    @DisplayName("구매 금액과 요청 금액이 다르면 예외가 발생한다")
    fun startPaymentPurchaseAmountMismatch() {
        // given
        val orderId = "GUDIT_test-order-id"
        val amount = 15000

        val purchase = mock(Purchase::class.java)
        val payment = Payment.create(
                purchase,
                amount
        )

        given(purchase.purchasePrice)
                .willReturn(20000)

        given(paymentRepository.findByOrderIdWithLock(orderId))
                .willReturn(Optional.of(payment))

        // when & then
        assertThatThrownBy {
            paymentTransactionService.startPayment(
                        orderId,
                        "payment-key",
                        amount
                )
        }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(java.util.function.Consumer<Throwable> { exception ->
                    val businessException =
                            exception as BusinessException

                    assertThat(businessException.errorCode)
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH
                            ) })
    }

    @Test
    @DisplayName("결제 승인 응답이 정상이라면 잠근 구매와 결제를 완료한다")
    fun completePayment() {
        // given
        val purchaseId = 100L

        val purchase = mock(Purchase::class.java)

        val payment = Payment.create(
                purchase,
                15_000
        )
        payment.start("payment-key")

        val response =
                mock(TossPaymentResponse::class.java)

        given(purchase.id)
                .willReturn(purchaseId)

        given(purchase.status)
                .willReturn(PurchaseStatus.PENDING_PAYMENT)

        given(response.orderId)
                .willReturn(payment.orderId)

        given(response.totalAmount)
                .willReturn(15_000)

        given(response.approvedAt)
                .willReturn(
                        OffsetDateTime.parse(
                                "2026-08-14T11:00:00+09:00"
                        )
                )

        given(paymentRepository.findByOrderIdWithLock(
                payment.orderId
        ))
                .willReturn(Optional.of(payment))

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(purchase))

        // when
        paymentTransactionService.completePayment(
                payment.orderId,
                response
        )

        // then
        assertThat(payment.status)
                .isEqualTo(PaymentStatus.DONE)

        assertThat(payment.approvedAt)
                .isEqualTo(
                        OffsetDateTime.parse(
                                "2026-08-14T11:00:00+09:00"
                        ).toLocalDateTime()
                )

        verify(paymentRepository)
                .findByOrderIdWithLock(
                        payment.orderId
                )

        verify(purchaseRepository)
                .findByIdWithLock(purchaseId)

        verify(purchase)
                .complete()
    }

    @Test
    @DisplayName("Toss 승인 응답의 orderId가 다르면 예외가 발생한다")
    fun completePaymentOrderIdMismatch() {
        // given
        val purchase = mock(Purchase::class.java)

        val payment = Payment.create(
                purchase,
                15000
        )

        payment.start("payment-key")

        val response = mock(TossPaymentResponse::class.java)

        given(response.orderId)
                .willReturn("GUDIT_other-order-id")

        given(paymentRepository.findByOrderIdWithLock(
                payment.orderId
        ))
                .willReturn(Optional.of(payment))

        // when & then
        assertThatThrownBy {
            paymentTransactionService.completePayment(
                        payment.orderId,
                        response
                )
        }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(java.util.function.Consumer<Throwable> { exception ->
                    val businessException =
                            exception as BusinessException

                    assertThat(businessException.errorCode)
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_ORDER_ID_MISMATCH
                            ) })
    }

    @Test
    @DisplayName("Toss 승인 응답 금액이 결제 금액과 다르면 예외가 발생한다")
    fun completePaymentAmountMismatch() {
        // given
        val purchase = mock(Purchase::class.java)

        val payment = Payment.create(
                purchase,
                15000
        )

        payment.start("payment-key")

        val response = mock(TossPaymentResponse::class.java)

        given(response.orderId)
                .willReturn(payment.orderId)

        given(response.totalAmount)
                .willReturn(20000)

        given(paymentRepository.findByOrderIdWithLock(
                payment.orderId
        ))
                .willReturn(Optional.of(payment))

        // when & then
        assertThatThrownBy {
            paymentTransactionService.completePayment(
                        payment.orderId,
                        response
                )
        }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(java.util.function.Consumer<Throwable> { exception ->
                    val businessException =
                            exception as BusinessException

                    assertThat(businessException.errorCode)
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH
                            ) })
    }

    @Test
    @DisplayName("결제 실패 시 잠근 구매가 PENDING_PAYMENT이면 취소하고 재고 복구 Outbox 이벤트를 저장한다")
    fun failPayment() {
        // given
        val purchaseId = 100L

        val purchase = mock(Purchase::class.java)
        val sale = mock(Sale::class.java)
        val user = mock(User::class.java)

        val payment = Payment.create(
                purchase,
                15_000
        )
        payment.start("payment-key")

        given(purchase.id)
                .willReturn(purchaseId)

        given(purchase.status)
                .willReturn(PurchaseStatus.PENDING_PAYMENT)

        given(purchase.sale)
                .willReturn(sale)

        given(purchase.user)
                .willReturn(user)

        given(purchase.quantity)
                .willReturn(1)

        given(sale.id)
                .willReturn(10L)

        given(user.id)
                .willReturn(1L)

        given(paymentRepository.findByOrderId(
                payment.orderId
        ))
                .willReturn(Optional.of(payment))

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(purchase))

        // when
        paymentTransactionService.failPayment(
                payment.orderId
        )

        // then
        assertThat(payment.status)
                .isEqualTo(PaymentStatus.FAILED)

        verify(purchaseRepository)
                .findByIdWithLock(purchaseId)

        verify(outboxEventService)
                .saveStockRestoreRequested(
                        purchaseId,
                        10L,
                        1L,
                        1
                )

        verify(purchase)
                .cancel()
    }

    @Test
    @DisplayName("승인 후 처리 실패 보상 시 잠근 구매를 취소하고 재고 복구 Outbox 이벤트를 저장한다")
    fun compensateApprovalFailure() {
        // given
        val purchaseId = 100L

        val purchase = mock(Purchase::class.java)
        val sale = mock(Sale::class.java)
        val user = mock(User::class.java)

        val payment = Payment.create(
                purchase,
                15_000
        )
        payment.start("payment-key")

        given(purchase.id)
                .willReturn(purchaseId)

        given(purchase.status)
                .willReturn(PurchaseStatus.PENDING_PAYMENT)

        given(purchase.sale)
                .willReturn(sale)

        given(purchase.user)
                .willReturn(user)

        given(purchase.quantity)
                .willReturn(1)

        given(sale.id)
                .willReturn(10L)

        given(user.id)
                .willReturn(1L)

        given(paymentRepository.findByPaymentKey("payment-key"))
                .willReturn(Optional.of(payment))

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(purchase))

        // when
        paymentTransactionService.compensateApprovalFailure(
                "payment-key"
        )

        // then
        assertThat(payment.status)
                .isEqualTo(PaymentStatus.CANCELED)

        assertThat(payment.canceledAt)
                .isNotNull()

        verify(purchaseRepository)
                .findByIdWithLock(purchaseId)

        verify(outboxEventService)
                .saveStockRestoreRequested(
                        purchaseId,
                        10L,
                        1L,
                        1
                )

        verify(purchase)
                .cancel()
    }

    @Test
    @DisplayName("완료된 결제를 취소하면 CANCELED 상태로 변경한다")
    fun completeCancel() {
        // given
        val purchase = mock(Purchase::class.java)

        val payment = Payment.create(
                purchase,
                15000
        )

        payment.start("payment-key")
        payment.complete(
                OffsetDateTime.parse(
                        "2026-08-14T11:00:00+09:00"
                ).toLocalDateTime()
        )

        given(paymentRepository.findByPaymentKey("payment-key"))
                .willReturn(Optional.of(payment))

        // when
        paymentTransactionService.completeCancel(
                "payment-key"
        )

        // then
        assertThat(payment.status)
                .isEqualTo(PaymentStatus.CANCELED)

        assertThat(payment.canceledAt)
                .isNotNull()
    }

    @Test
    @DisplayName("존재하지 않는 orderId로 결제를 조회하면 예외가 발생한다")
    fun getPaymentByOrderIdNotFound() {
        // given
        given(paymentRepository.findByOrderId("unknown-order-id"))
                .willReturn(Optional.empty())

        // when & then
        assertThatThrownBy {
            paymentTransactionService.getPaymentByOrderId(
                        "unknown-order-id"
                )
        }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(java.util.function.Consumer<Throwable> { exception ->
                    val businessException =
                            exception as BusinessException

                    assertThat(businessException.errorCode)
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_NOT_FOUND
                            ) })
    }

    @Test
    @DisplayName("존재하지 않는 paymentKey로 결제를 조회하면 예외가 발생한다")
    fun getPaymentByPaymentKeyNotFound() {
        // given
        given(paymentRepository.findByPaymentKey("unknown-payment-key"))
                .willReturn(Optional.empty())

        // when & then
        assertThatThrownBy {
            paymentTransactionService.getPaymentByPaymentKey(
                        "unknown-payment-key"
                )
        }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(java.util.function.Consumer<Throwable> { exception ->
                    val businessException =
                            exception as BusinessException

                    assertThat(businessException.errorCode)
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_NOT_FOUND
                            ) })
    }

    @Test
    @DisplayName("DONE Webhook 보정 시 READY 결제와 PENDING_PAYMENT 구매를 완료한다")
    fun reconcileDone() {
        // given
        val purchaseId = 100L

        val purchase = mock(Purchase::class.java)

        val payment = Payment.create(
                purchase,
                15_000
        )

        val response =
                mock(TossPaymentResponse::class.java)

        given(purchase.id)
                .willReturn(purchaseId)

        given(purchase.status)
                .willReturn(PurchaseStatus.PENDING_PAYMENT)

        given(response.orderId)
                .willReturn(payment.orderId)

        given(response.paymentKey)
                .willReturn("payment-key")

        given(response.totalAmount)
                .willReturn(15_000)

        given(response.approvedAt)
                .willReturn(
                        OffsetDateTime.parse(
                                "2026-08-20T17:00:00+09:00"
                        )
                )

        given(paymentRepository.findByOrderId(
                payment.orderId
        ))
                .willReturn(Optional.of(payment))

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(purchase))

        // when
        paymentTransactionService.reconcileDone(response)

        // then
        assertThat(payment.status)
                .isEqualTo(PaymentStatus.DONE)

        assertThat(payment.paymentKey)
                .isEqualTo("payment-key")

        verify(purchaseRepository)
                .findByIdWithLock(purchaseId)

        verify(purchase)
                .complete()
    }

    @Test
    @DisplayName("CANCELED Webhook 보정 시 결제와 구매를 취소하고 재고 복구 Outbox 이벤트를 저장한다")
    fun reconcileCanceled() {
        // given
        val purchaseId = 100L
        val sale = mock(Sale::class.java)
        val user = mock(User::class.java)
        val purchase = mock(Purchase::class.java)

        val payment = Payment.create(
                purchase,
                15_000
        )

        val response =
                mock(TossPaymentResponse::class.java)

        given(purchase.id)
                .willReturn(purchaseId)

        given(purchase.status)
                .willReturn(PurchaseStatus.PENDING_PAYMENT)

        given(purchase.sale)
                .willReturn(sale)

        given(purchase.user)
                .willReturn(user)

        given(purchase.quantity)
                .willReturn(1)

        given(sale.id)
                .willReturn(10L)

        given(user.id)
                .willReturn(1L)

        given(response.orderId)
                .willReturn(payment.orderId)

        given(response.totalAmount)
                .willReturn(15_000)

        given(paymentRepository.findByOrderId(
                payment.orderId
        ))
                .willReturn(Optional.of(payment))

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(purchase))

        // when
        paymentTransactionService.reconcileCanceled(response)

        // then
        assertThat(payment.status)
                .isEqualTo(PaymentStatus.CANCELED)

        verify(purchase)
                .cancel()

        verify(outboxEventService)
                .saveStockRestoreRequested(
                        purchaseId,
                        10L,
                        1L,
                        1
                )
    }

    @Test
    @DisplayName("ABORTED Webhook 보정 시 결제를 실패 처리하고 구매 취소와 재고 복구 Outbox 이벤트를 저장한다")
    fun reconcileAborted() {
        // given
        val purchaseId = 100L
        val sale = mock(Sale::class.java)
        val user = mock(User::class.java)
        val purchase = mock(Purchase::class.java)

        val payment = Payment.create(
                purchase,
                15_000
        )

        val response =
                mock(TossPaymentResponse::class.java)

        given(purchase.id)
                .willReturn(purchaseId)

        given(purchase.status)
                .willReturn(PurchaseStatus.PENDING_PAYMENT)

        given(purchase.sale)
                .willReturn(sale)

        given(purchase.user)
                .willReturn(user)

        given(purchase.quantity)
                .willReturn(1)

        given(sale.id)
                .willReturn(10L)

        given(user.id)
                .willReturn(1L)

        given(response.orderId)
                .willReturn(payment.orderId)

        given(response.totalAmount)
                .willReturn(15_000)

        given(paymentRepository.findByOrderId(
                payment.orderId
        ))
                .willReturn(Optional.of(payment))

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(purchase))

        // when
        paymentTransactionService.reconcileAborted(response)

        // then
        assertThat(payment.status)
                .isEqualTo(PaymentStatus.FAILED)

        verify(purchase)
                .cancel()

        verify(outboxEventService)
                .saveStockRestoreRequested(
                        purchaseId,
                        10L,
                        1L,
                        1
                )
    }

    @Test
    @DisplayName("EXPIRED Webhook 보정 시 결제와 구매를 취소하고 재고 복구 Outbox 이벤트를 저장한다")
    fun reconcileExpired() {
        // given
        val purchaseId = 100L
        val sale = mock(Sale::class.java)
        val user = mock(User::class.java)
        val purchase = mock(Purchase::class.java)

        val payment = Payment.create(
                purchase,
                15_000
        )

        val response =
                mock(TossPaymentResponse::class.java)

        given(purchase.id)
                .willReturn(purchaseId)

        given(purchase.status)
                .willReturn(PurchaseStatus.PENDING_PAYMENT)

        given(purchase.sale)
                .willReturn(sale)

        given(purchase.user)
                .willReturn(user)

        given(purchase.quantity)
                .willReturn(1)

        given(sale.id)
                .willReturn(10L)

        given(user.id)
                .willReturn(1L)

        given(response.orderId)
                .willReturn(payment.orderId)

        given(response.totalAmount)
                .willReturn(15_000)

        given(paymentRepository.findByOrderId(
                payment.orderId
        ))
                .willReturn(Optional.of(payment))

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(purchase))

        // when
        paymentTransactionService.reconcileExpired(response)

        // then
        assertThat(payment.status)
                .isEqualTo(PaymentStatus.CANCELED)

        verify(purchase)
                .cancel()

        verify(outboxEventService)
                .saveStockRestoreRequested(
                        purchaseId,
                        10L,
                        1L,
                        1
                )
    }

    @Test
    @DisplayName("이미 CANCELED인 결제의 Webhook을 다시 처리해도 재고를 중복 복구하지 않는다")
    fun reconcileCanceledAlreadyCanceled() {
        // given
        val purchaseId = 100L

        val purchase = mock(Purchase::class.java)

        val payment = Payment.create(
                purchase,
                15_000
        )

        payment.cancelByWebhook()

        val response =
                mock(TossPaymentResponse::class.java)

        given(purchase.id)
                .willReturn(purchaseId)

        given(response.orderId)
                .willReturn(payment.orderId)

        given(response.totalAmount)
                .willReturn(15_000)

        given(paymentRepository.findByOrderId(
                payment.orderId
        ))
                .willReturn(Optional.of(payment))

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(purchase))

        // when
        paymentTransactionService.reconcileCanceled(response)

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

        verify(purchase, never())
                .cancel()
    }

    @Test
    @DisplayName("이미 FAILED인 결제의 ABORTED Webhook을 다시 처리해도 재고를 중복 복구하지 않는다")
    fun reconcileAbortedAlreadyFailed() {
        // given
        val purchaseId = 100L

        val purchase = mock(Purchase::class.java)

        val payment = Payment.create(
                purchase,
                15_000
        )

        payment.failByWebhook()

        val response =
                mock(TossPaymentResponse::class.java)

        given(purchase.id)
                .willReturn(purchaseId)

        given(response.orderId)
                .willReturn(payment.orderId)

        given(response.totalAmount)
                .willReturn(15_000)

        given(paymentRepository.findByOrderId(
                payment.orderId
        ))
                .willReturn(Optional.of(payment))

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(purchase))

        // when
        paymentTransactionService.reconcileAborted(response)

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

        verify(purchase, never())
                .cancel()
    }

    @Test
    @DisplayName("이미 DONE인 결제의 Webhook을 다시 처리해도 중복 완료 처리하지 않는다")
    fun reconcileDoneAlreadyDone() {
        // given
        val purchaseId = 100L

        val purchase = mock(Purchase::class.java)

        val payment = Payment.create(
                purchase,
                15_000
        )

        payment.start("payment-key")
        payment.complete(
                OffsetDateTime.parse(
                        "2026-08-20T17:00:00+09:00"
                ).toLocalDateTime()
        )

        val response =
                mock(TossPaymentResponse::class.java)

        given(purchase.id)
                .willReturn(purchaseId)

        given(response.orderId)
                .willReturn(payment.orderId)

        given(response.totalAmount)
                .willReturn(15_000)

        given(paymentRepository.findByOrderId(
                payment.orderId
        ))
                .willReturn(Optional.of(payment))

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(purchase))

        // when
        paymentTransactionService.reconcileDone(response)

        // then
        assertThat(payment.status)
                .isEqualTo(PaymentStatus.DONE)

        verify(purchase, never())
                .complete()

        verify(outboxEventService, never())
                .saveStockRestoreRequested(
                        anyLong(),
                        anyLong(),
                        anyLong(),
                        anyInt()
                )
    }

    @Test
    @DisplayName("이미 CANCELED인 결제의 승인 실패 보상을 다시 처리해도 재고 복구 Outbox를 중복 저장하지 않는다")
    fun compensateApprovalFailureAlreadyCanceled() {
        // given
        val purchaseId = 100L

        val purchase = mock(Purchase::class.java)

        val payment = Payment.create(
                purchase,
                15_000
        )

        payment.start("payment-key")
        payment.cancelAfterApprovalFailure()

        given(purchase.id)
                .willReturn(purchaseId)

        given(paymentRepository.findByPaymentKey("payment-key"))
                .willReturn(Optional.of(payment))

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(purchase))

        // when
        paymentTransactionService.compensateApprovalFailure(
                "payment-key"
        )

        // then
        assertThat(payment.status)
                .isEqualTo(PaymentStatus.CANCELED)

        verify(purchase, never())
                .cancel()

        verify(outboxEventService, never())
                .saveStockRestoreRequested(
                        anyLong(),
                        anyLong(),
                        anyLong(),
                        anyInt()
                )
    }

    @Test
    @DisplayName("결제 보상 재처리 요청 시 PAYMENT_COMPENSATION_REQUIRED Outbox 이벤트를 저장한다")
    fun requestPaymentCompensation() {
        // given
        val purchase = mock(Purchase::class.java)

        val payment = Payment.create(
                purchase,
                15_000
        )

        payment.start("payment-key")

        given(paymentRepository.findByPaymentKey("payment-key"))
                .willReturn(Optional.of(payment))

        // when
        paymentTransactionService.requestPaymentCompensation(
                "payment-key"
        )

        // then
        verify(outboxEventService)
                .savePaymentCompensationRequired(
                        null,
                        payment.orderId,
                        "payment-key"
                )
    }
}