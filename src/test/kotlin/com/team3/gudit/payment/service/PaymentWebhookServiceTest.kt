package com.team3.gudit.payment.service

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.payment.client.TossPaymentClient
import com.team3.gudit.payment.dto.TossPaymentResponse
import com.team3.gudit.payment.dto.TossPaymentWebhookRequest
import com.team3.gudit.payment.entity.Payment
import com.team3.gudit.payment.entity.PaymentStatus
import com.team3.gudit.payment.exception.PaymentErrorCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

import java.time.LocalDateTime
import java.time.OffsetDateTime

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

@ExtendWith(MockitoExtension::class)
class PaymentWebhookServiceTest {

    @Mock
    private lateinit var tossPaymentClient: TossPaymentClient

    @Mock
    private lateinit var paymentService: PaymentService

    @Mock
    private lateinit var paymentTransactionService: PaymentTransactionService

    @InjectMocks
    private lateinit var paymentWebhookService: PaymentWebhookService

    @Test
    @DisplayName("DONE Webhook을 수신하면 Toss 결제를 재조회하고 완료 상태를 보정한다")
    fun handleDone() {
        // given
        val request =
                createWebhookRequest("DONE")

        val actualPayment =
                createPaymentResponse("DONE")

        val payment = org.mockito.Mockito.mock(Payment::class.java)

        given(tossPaymentClient.getPayment("payment-key"))
                .willReturn(actualPayment)

        given(paymentTransactionService.getPaymentByOrderId(
                "GUDIT_test-order-id"
        ))
                .willReturn(payment)

        given(payment.status)
                .willReturn(PaymentStatus.READY)

        // when
        paymentWebhookService.handle(request)

        // then
        verify(tossPaymentClient)
                .getPayment("payment-key")

        verify(paymentTransactionService)
                .reconcileDone(actualPayment)

        verify(paymentService, never())
                .cancelPayment("payment-key")
    }

    @Test
    @DisplayName("이미 취소된 결제에 DONE Webhook이 수신되면 Toss 결제를 보상 취소한다")
    fun handleDoneWhenPaymentCanceled() {
        // given
        val request =
                createWebhookRequest("DONE")

        val actualPayment =
                createPaymentResponse("DONE")

        val payment = org.mockito.Mockito.mock(Payment::class.java)

        given(tossPaymentClient.getPayment("payment-key"))
                .willReturn(actualPayment)

        given(paymentTransactionService.getPaymentByOrderId(
                "GUDIT_test-order-id"
        ))
                .willReturn(payment)

        given(payment.status)
                .willReturn(PaymentStatus.CANCELED)

        // when
        paymentWebhookService.handle(request)

        // then
        verify(paymentService)
                .cancelPayment("payment-key")

        verify(paymentTransactionService, never())
                .reconcileDone(actualPayment)
    }

    @Test
    @DisplayName("CANCELED Webhook을 수신하면 취소 상태를 보정한다")
    fun handleCanceled() {
        // given
        val request =
                createWebhookRequest("CANCELED")

        val actualPayment =
                createPaymentResponse("CANCELED")

        given(tossPaymentClient.getPayment("payment-key"))
                .willReturn(actualPayment)

        // when
        paymentWebhookService.handle(request)

        // then
        verify(paymentTransactionService)
                .reconcileCanceled(actualPayment)
    }

    @Test
    @DisplayName("ABORTED Webhook을 수신하면 실패 상태를 보정한다")
    fun handleAborted() {
        // given
        val request =
                createWebhookRequest("ABORTED")

        val actualPayment =
                createPaymentResponse("ABORTED")

        given(tossPaymentClient.getPayment("payment-key"))
                .willReturn(actualPayment)

        // when
        paymentWebhookService.handle(request)

        // then
        verify(paymentTransactionService)
                .reconcileAborted(actualPayment)
    }

    @Test
    @DisplayName("EXPIRED Webhook을 수신하면 만료 상태를 보정한다")
    fun handleExpired() {
        // given
        val request =
                createWebhookRequest("EXPIRED")

        val actualPayment =
                createPaymentResponse("EXPIRED")

        given(tossPaymentClient.getPayment("payment-key"))
                .willReturn(actualPayment)

        // when
        paymentWebhookService.handle(request)

        // then
        verify(paymentTransactionService)
                .reconcileExpired(actualPayment)
    }

    @Test
    @DisplayName("중간 결제 상태 Webhook은 상태를 보정하지 않는다")
    fun handleInProgress() {
        // given
        val request =
                createWebhookRequest("IN_PROGRESS")

        val actualPayment =
                createPaymentResponse("IN_PROGRESS")

        given(tossPaymentClient.getPayment("payment-key"))
                .willReturn(actualPayment)

        // when
        paymentWebhookService.handle(request)

        // then
        verify(paymentTransactionService, never())
                .reconcileDone(actualPayment)

        verify(paymentTransactionService, never())
                .reconcileCanceled(actualPayment)

        verify(paymentTransactionService, never())
                .reconcileAborted(actualPayment)

        verify(paymentTransactionService, never())
                .reconcileExpired(actualPayment)
    }

    @Test
    @DisplayName("Webhook paymentKey와 Toss 재조회 결과가 다르면 예외가 발생한다")
    fun handlePaymentKeyMismatch() {
        // given
        val request =
                createWebhookRequest("DONE")

        val actualPayment =
                TossPaymentResponse(
                        "other-payment-key",
                        "GUDIT_test-order-id",
                        "DONE",
                        15_000,
                        OffsetDateTime.parse(
                                "2026-08-20T17:00:00+09:00"
                        )
                )

        given(tossPaymentClient.getPayment("payment-key"))
                .willReturn(actualPayment)

        // when & then
        assertThatThrownBy {
            paymentWebhookService.handle(request)
        }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(java.util.function.Consumer<Throwable> { exception ->
                    val businessException =
                            exception as BusinessException

                    assertThat(businessException.errorCode)
                            .isEqualTo(
                                    PaymentErrorCode
                                            .PAYMENT_WEBHOOK_VALIDATION_FAILED
                            ) })
    }

    @Test
    @DisplayName("Webhook orderId와 Toss 재조회 결과가 다르면 예외가 발생한다")
    fun handleOrderIdMismatch() {
        // given
        val request =
                createWebhookRequest("DONE")

        val actualPayment =
                TossPaymentResponse(
                        "payment-key",
                        "GUDIT_other-order-id",
                        "DONE",
                        15_000,
                        OffsetDateTime.parse(
                                "2026-08-20T17:00:00+09:00"
                        )
                )

        given(tossPaymentClient.getPayment("payment-key"))
                .willReturn(actualPayment)

        // when & then
        assertThatThrownBy {
            paymentWebhookService.handle(request)
        }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(java.util.function.Consumer<Throwable> { exception ->
                    val businessException =
                            exception as BusinessException

                    assertThat(businessException.errorCode)
                            .isEqualTo(
                                    PaymentErrorCode
                                            .PAYMENT_WEBHOOK_VALIDATION_FAILED
                            ) })
    }

    @Test
    @DisplayName("Webhook 결제 금액과 Toss 재조회 결과가 다르면 예외가 발생한다")
    fun handleAmountMismatch() {
        // given
        val request =
                createWebhookRequest("DONE")

        val actualPayment =
                TossPaymentResponse(
                        "payment-key",
                        "GUDIT_test-order-id",
                        "DONE",
                        20_000,
                        OffsetDateTime.parse(
                                "2026-08-20T17:00:00+09:00"
                        )
                )

        given(tossPaymentClient.getPayment("payment-key"))
                .willReturn(actualPayment)

        // when & then
        assertThatThrownBy {
            paymentWebhookService.handle(request)
        }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(java.util.function.Consumer<Throwable> { exception ->
                    val businessException =
                            exception as BusinessException

                    assertThat(businessException.errorCode)
                            .isEqualTo(
                                    PaymentErrorCode
                                            .PAYMENT_WEBHOOK_VALIDATION_FAILED
                            ) })
    }

    @Test
    @DisplayName("PAYMENT_STATUS_CHANGED가 아닌 Webhook은 처리하지 않는다")
    fun ignoreOtherEventType() {
        // given
        val request =
                TossPaymentWebhookRequest(
                        "OTHER_EVENT",
                        LocalDateTime.now(),
                        createPaymentResponse("DONE")
                )

        // when
        paymentWebhookService.handle(request)

        // then
        verify(tossPaymentClient, never())
                .getPayment("payment-key")
    }

    private fun createWebhookRequest(
            status: String
    ): TossPaymentWebhookRequest {
        return TossPaymentWebhookRequest(
                "PAYMENT_STATUS_CHANGED",
                LocalDateTime.parse(
                        "2026-08-20T17:00:00"
                ),
                createPaymentResponse(status)
        )
    }

    private fun createPaymentResponse(
            status: String
    ): TossPaymentResponse {
        return TossPaymentResponse(
                "payment-key",
                "GUDIT_test-order-id",
                status,
                15_000,
                OffsetDateTime.parse(
                        "2026-08-20T17:00:00+09:00"
                )
        )
    }
}