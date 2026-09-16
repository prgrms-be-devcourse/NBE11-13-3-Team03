package com.team3.gudit.payment.service

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.payment.client.TossPaymentClient
import com.team3.gudit.payment.dto.*
import com.team3.gudit.payment.entity.Payment
import com.team3.gudit.payment.entity.PaymentStatus
import com.team3.gudit.payment.exception.PaymentErrorCode
import com.team3.gudit.payment.exception.TossPaymentException
import com.team3.gudit.payment.repository.PaymentRepository
import com.team3.gudit.purchase.entity.Purchase
import com.team3.gudit.purchase.entity.PurchaseStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

import java.util.Optional

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(type: Class<T>): T {
    ArgumentMatchers.any(type)
    return null as T
}

private fun <T> eqNonNull(value: T): T {
    ArgumentMatchers.eq(value)
    return value
}

@ExtendWith(MockitoExtension::class)
class PaymentServiceTest {

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var tossPaymentClient: TossPaymentClient

    @Mock
    private lateinit var paymentTransactionService: PaymentTransactionService

    @InjectMocks
    private lateinit var paymentService: PaymentService

    @Test
    @DisplayName("구매 정보로 READY 상태의 결제를 생성한다")
    fun createPayment() {
        // given
        val purchase = mock(Purchase::class.java)

        given(purchase.purchasePrice)
                .willReturn(15000)

        given(paymentRepository.save(anyNonNull(Payment::class.java)))
                .willAnswer { invocation -> invocation.getArgument<Payment>(0) }

        // when
        val payment = paymentService.createPayment(purchase)

        // then
        assertThat(payment.purchase)
                .isEqualTo(purchase)

        assertThat(payment.amount)
                .isEqualTo(15000)

        assertThat(payment.orderId)
                .startsWith("GUDIT_")

        verify(paymentRepository)
                .save(anyNonNull(Payment::class.java))
    }

    @Test
    @DisplayName("결제 승인에 성공하면 결제 시작 후 Toss 승인과 완료 처리를 수행한다")
    fun confirmSuccess() {
        // given
        val paymentKey = "payment-key"
        val orderId = "GUDIT_test-order-id"
        val amount = 15000

        val request =
                PaymentConfirmRequest(
                        paymentKey,
                        orderId,
                        amount
                )

        val response =
                mock(TossPaymentResponse::class.java)

        given(tossPaymentClient.confirm(
                anyNonNull(TossPaymentConfirmRequest::class.java),
                eqNonNull("GUDIT_CONFIRM_" + orderId)
        ))
                .willReturn(response)

        // when
        val result =
                paymentService.confirm(request)

        // then
        assertThat(result)
                .isSameAs(response)

        verify(paymentTransactionService)
                .startPayment(
                        orderId,
                        paymentKey,
                        amount
                )

        verify(tossPaymentClient)
                .confirm(
                        anyNonNull(TossPaymentConfirmRequest::class.java),
                        eqNonNull("GUDIT_CONFIRM_" + orderId)
                )

        verify(paymentTransactionService)
                .completePayment(
                        orderId,
                        response
                )
    }

    @Test
    @DisplayName("카드사 거절처럼 명확한 승인 실패는 결제 실패 처리 후 예외를 발생시킨다")
    fun confirmDefiniteFailure() {
        // given
        val paymentKey = "payment-key"
        val orderId = "GUDIT_test-order-id"
        val amount = 15000

        val request =
                PaymentConfirmRequest(
                        paymentKey,
                        orderId,
                        amount
                )

        given(tossPaymentClient.confirm(
                anyNonNull(TossPaymentConfirmRequest::class.java),
                eqNonNull("GUDIT_CONFIRM_" + orderId)
        ))
                .willThrow(
                        TossPaymentException(
                                "REJECT_CARD_COMPANY",
                                "카드사에서 결제를 거절했습니다."
                        )
                )

        // when & then
        assertThatThrownBy {
            paymentService.confirm(request)
        }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(java.util.function.Consumer<Throwable> { exception ->
                    val businessException =
                            exception as BusinessException

                    assertThat(businessException.errorCode)
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_CONFIRM_FAILED
                            ) })

        verify(paymentTransactionService)
                .startPayment(
                        orderId,
                        paymentKey,
                        amount
                )

        verify(paymentTransactionService)
                .failPayment(orderId)

        verify(tossPaymentClient, never())
                .getPayment(paymentKey)
    }

    @Test
    @DisplayName("결제 승인 결과가 불확실하지만 재조회 결과가 DONE이면 결제를 완료한다")
    fun confirmUncertainFailureReconcileDone() {
        // given
        val paymentKey = "payment-key"
        val orderId = "GUDIT_test-order-id"
        val amount = 15000

        val request =
                PaymentConfirmRequest(
                        paymentKey,
                        orderId,
                        amount
                )

        val response =
                mock(TossPaymentResponse::class.java)

        given(tossPaymentClient.confirm(
                anyNonNull(TossPaymentConfirmRequest::class.java),
                eqNonNull("GUDIT_CONFIRM_" + orderId)
        ))
                .willThrow(
                        TossPaymentException(
                                "NETWORK_ERROR",
                                "네트워크 오류"
                        )
                )

        given(tossPaymentClient.getPayment(paymentKey))
                .willReturn(response)

        given(response.status)
                .willReturn("DONE")

        // when
        val result =
                paymentService.confirm(request)

        // then
        assertThat(result)
                .isSameAs(response)

        verify(tossPaymentClient)
                .getPayment(paymentKey)

        verify(paymentTransactionService)
                .completePayment(
                        orderId,
                        response
                )

        verify(paymentTransactionService, never())
                .failPayment(orderId)
    }

    @Test
    @DisplayName("결제 승인 결과가 불확실하고 재조회 결과도 DONE이 아니면 처리 중 예외가 발생한다")
    fun confirmUncertainFailureReconcileNotDone() {
        // given
        val paymentKey = "payment-key"
        val orderId = "GUDIT_test-order-id"
        val amount = 15000

        val request =
                PaymentConfirmRequest(
                        paymentKey,
                        orderId,
                        amount
                )

        val response =
                mock(TossPaymentResponse::class.java)

        given(tossPaymentClient.confirm(
                anyNonNull(TossPaymentConfirmRequest::class.java),
                eqNonNull("GUDIT_CONFIRM_" + orderId)
        ))
                .willThrow(
                        TossPaymentException(
                                "NETWORK_ERROR",
                                "네트워크 오류"
                        )
                )

        given(tossPaymentClient.getPayment(paymentKey))
                .willReturn(response)

        given(response.status)
                .willReturn("IN_PROGRESS")

        // when & then
        assertThatThrownBy {
            paymentService.confirm(request)
        }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(java.util.function.Consumer<Throwable> { exception ->
                    val businessException =
                            exception as BusinessException

                    assertThat(businessException.errorCode)
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_PROCESSING_ERROR
                            ) })

        verify(tossPaymentClient)
                .getPayment(paymentKey)

        verify(paymentTransactionService, never())
                .failPayment(orderId)

        verify(paymentTransactionService, never())
                .completePayment(
                        eqNonNull(orderId),
                        anyNonNull(TossPaymentResponse::class.java)
                )
    }

    @Test
    @DisplayName("결제 승인 결과가 불확실하고 Toss 재조회도 실패하면 처리 중 예외가 발생한다")
    fun confirmUncertainFailureReconcileFailure() {
        // given
        val paymentKey = "payment-key"
        val orderId = "GUDIT_test-order-id"
        val amount = 15000

        val request =
                PaymentConfirmRequest(
                        paymentKey,
                        orderId,
                        amount
                )

        given(tossPaymentClient.confirm(
                anyNonNull(TossPaymentConfirmRequest::class.java),
                eqNonNull("GUDIT_CONFIRM_" + orderId)
        ))
                .willThrow(
                        TossPaymentException(
                                "NETWORK_ERROR",
                                "승인 요청 네트워크 오류"
                        )
                )

        given(tossPaymentClient.getPayment(paymentKey))
                .willThrow(
                        TossPaymentException(
                                "NETWORK_ERROR",
                                "조회 요청 네트워크 오류"
                        )
                )

        // when & then
        assertThatThrownBy {
            paymentService.confirm(request)
        }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(java.util.function.Consumer<Throwable> { exception ->
                    val businessException =
                            exception as BusinessException

                    assertThat(businessException.errorCode)
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_PROCESSING_ERROR
                            ) })

        verify(tossPaymentClient)
                .getPayment(paymentKey)

        verify(paymentTransactionService, never())
                .failPayment(orderId)
    }

    @Test
    @DisplayName("Toss 승인은 성공했지만 DB 완료 처리에 실패하면 승인 취소 보상을 수행한다")
    fun confirmFinalizationFailureCompensates() {
        // given
        val paymentKey = "payment-key"
        val orderId = "GUDIT_test-order-id"
        val amount = 15000

        val request =
                PaymentConfirmRequest(
                        paymentKey,
                        orderId,
                        amount
                )

        val response =
                mock(TossPaymentResponse::class.java)

        given(tossPaymentClient.confirm(
                anyNonNull(TossPaymentConfirmRequest::class.java),
                eqNonNull("GUDIT_CONFIRM_" + orderId)
        ))
                .willReturn(response)

        willThrow(RuntimeException("DB 처리 실패"))
                .given(paymentTransactionService)
                .completePayment(
                        orderId,
                        response
                )

        // when & then
        assertThatThrownBy {
            paymentService.confirm(request)
        }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(java.util.function.Consumer<Throwable> { exception ->
                    val businessException =
                            exception as BusinessException

                    assertThat(businessException.errorCode)
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_FINALIZATION_FAILED
                            ) })

        verify(tossPaymentClient)
                .cancel(
                        eqNonNull(paymentKey),
                        anyNonNull(TossPaymentCancelRequest::class.java),
                        eqNonNull("GUDIT_CANCEL_" + paymentKey)
                )

        verify(paymentTransactionService)
                .compensateApprovalFailure(paymentKey)

        verify(paymentTransactionService, never())
                .requestPaymentCompensation(paymentKey)
    }

    @Test
    @DisplayName("승인 후 DB 실패에 대한 보상 취소까지 실패하면 보상 재처리 요청을 저장하고 예외가 발생한다")
    fun confirmCompensationFailure() {
        // given
        val paymentKey = "payment-key"
        val orderId = "GUDIT_test-order-id"
        val amount = 15000

        val request =
                PaymentConfirmRequest(
                        paymentKey,
                        orderId,
                        amount
                )

        val response =
                mock(TossPaymentResponse::class.java)

        given(tossPaymentClient.confirm(
                anyNonNull(TossPaymentConfirmRequest::class.java),
                eqNonNull("GUDIT_CONFIRM_" + orderId)
        ))
                .willReturn(response)

        willThrow(RuntimeException("DB 처리 실패"))
                .given(paymentTransactionService)
                .completePayment(
                        orderId,
                        response
                )

        given(tossPaymentClient.cancel(
                eqNonNull(paymentKey),
                anyNonNull(TossPaymentCancelRequest::class.java),
                eqNonNull("GUDIT_CANCEL_" + paymentKey)
        ))
                .willThrow(
                        RuntimeException("Toss 보상 취소 실패")
                )

        // when & then
        assertThatThrownBy {
            paymentService.confirm(request)
        }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(java.util.function.Consumer<Throwable> { exception ->
                    val businessException =
                            exception as BusinessException

                    assertThat(businessException.errorCode)
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_COMPENSATION_FAILED
                            ) })

        verify(paymentTransactionService, never())
                .compensateApprovalFailure(paymentKey)

        verify(paymentTransactionService)
                .requestPaymentCompensation(paymentKey)
    }

    @Test
    @DisplayName("재조회 결과 DONE 이후 DB 완료 처리에 실패해도 승인 취소 보상을 수행한다")
    fun reconcileDoneFinalizationFailureCompensates() {
        // given
        val paymentKey = "payment-key"
        val orderId = "GUDIT_test-order-id"
        val amount = 15000

        val request =
                PaymentConfirmRequest(
                        paymentKey,
                        orderId,
                        amount
                )

        val response =
                mock(TossPaymentResponse::class.java)

        given(tossPaymentClient.confirm(
                anyNonNull(TossPaymentConfirmRequest::class.java),
                eqNonNull("GUDIT_CONFIRM_" + orderId)
        ))
                .willThrow(
                        TossPaymentException(
                                "NETWORK_ERROR",
                                "승인 요청 네트워크 오류"
                        )
                )

        given(tossPaymentClient.getPayment(paymentKey))
                .willReturn(response)

        given(response.status)
                .willReturn("DONE")

        willThrow(RuntimeException("DB 처리 실패"))
                .given(paymentTransactionService)
                .completePayment(
                        orderId,
                        response
                )

        // when & then
        assertThatThrownBy {
            paymentService.confirm(request)
        }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(java.util.function.Consumer<Throwable> { exception ->
                    val businessException =
                            exception as BusinessException

                    assertThat(businessException.errorCode)
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_FINALIZATION_FAILED
                            ) })

        verify(tossPaymentClient)
                .getPayment(paymentKey)

        verify(tossPaymentClient)
                .cancel(
                        eqNonNull(paymentKey),
                        anyNonNull(TossPaymentCancelRequest::class.java),
                        eqNonNull("GUDIT_CANCEL_" + paymentKey)
                )

        verify(paymentTransactionService)
                .compensateApprovalFailure(paymentKey)
    }

    @Test
    @DisplayName("결제 완료 후 취소하면 Toss 결제를 취소하고 DB 취소 상태를 반영한다")
    fun cancelCompletedPayment() {
        // given
        val paymentKey = "payment-key"

        // when
        paymentService.cancelCompletedPayment(paymentKey)

        // then
        verify(tossPaymentClient)
                .cancel(
                        eqNonNull(paymentKey),
                        anyNonNull(TossPaymentCancelRequest::class.java),
                        eqNonNull("GUDIT_CANCEL_" + paymentKey)
                )

        verify(paymentTransactionService)
                .completeCancel(paymentKey)
    }

    @Test
    @DisplayName("구매 ID로 결제 정보를 조회한다")
    fun getPaymentByPurchaseId() {
        // given
        val purchaseId = 100L
        val payment = mock(Payment::class.java)

        given(paymentRepository.findByPurchaseId(purchaseId))
                .willReturn(Optional.of(payment))

        // when
        val result =
                paymentService.getPaymentByPurchaseId(purchaseId)

        // then
        assertThat(result)
                .isSameAs(payment)
    }

    @Test
    @DisplayName("구매 ID에 해당하는 결제가 없으면 예외가 발생한다")
    fun getPaymentByPurchaseIdNotFound() {
        // given
        val purchaseId = 100L

        given(paymentRepository.findByPurchaseId(purchaseId))
                .willReturn(Optional.empty())

        // when & then
        assertThatThrownBy {
            paymentService.getPaymentByPurchaseId(
                        purchaseId
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
    @DisplayName("주문번호로 결제와 구매 상태를 조회한다")
    fun getStatus() {
        // given
        val orderId = "GUDIT_test-order-id"

        val payment = mock(Payment::class.java)
        val purchase = mock(Purchase::class.java)

        given(paymentRepository.findByOrderId(orderId))
                .willReturn(Optional.of(payment))

        given(payment.orderId)
                .willReturn(orderId)

        given(payment.purchase)
                .willReturn(purchase)

        given(payment.status)
                .willReturn(PaymentStatus.DONE)

        given(payment.amount)
                .willReturn(15000)

        given(purchase.id)
                .willReturn(100L)

        given(purchase.status)
                .willReturn(PurchaseStatus.PURCHASED)

        // when
        val response =
                paymentService.getStatus(orderId)

        // then
        assertThat(response.orderId)
                .isEqualTo(orderId)

        assertThat(response.purchaseId)
                .isEqualTo(100L)

        assertThat(response.purchaseStatus)
                .isEqualTo(PurchaseStatus.PURCHASED)

        assertThat(response.paymentStatus)
                .isEqualTo(PaymentStatus.DONE)

        assertThat(response.amount)
                .isEqualTo(15000)

        verify(paymentRepository)
                .findByOrderId(orderId)
    }

    @Test
    @DisplayName("주문번호에 해당하는 결제가 없으면 예외가 발생한다")
    fun getStatusNotFound() {
        // given
        val orderId = "GUDIT_not-found"

        given(paymentRepository.findByOrderId(orderId))
                .willReturn(Optional.empty())

        // when & then
        assertThatThrownBy {
            paymentService.getStatus(orderId)
        }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(java.util.function.Consumer<Throwable> { exception ->
                    val businessException =
                            exception as BusinessException

                    assertThat(businessException.errorCode)
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_NOT_FOUND
                            ) })

        verify(paymentRepository)
                .findByOrderId(orderId)
    }
}

