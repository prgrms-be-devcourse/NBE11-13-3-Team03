package com.team3.gudit.payment.entity

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.purchase.entity.Purchase
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class PaymentTest {
    @Test
    @DisplayName("결제 정보를 생성하면 READY 상태로 생성된다")
    fun createPayment() {
        val purchase = mock(Purchase::class.java)
        val payment = Payment.create(purchase, 15000)

        assertThat(payment.purchase).isEqualTo(purchase)
        assertThat(payment.amount).isEqualTo(15000)
        assertThat(payment.status).isEqualTo(PaymentStatus.READY)
        assertThat(payment.orderId).startsWith("GUDIT_")
        assertThat(payment.paymentKey).isNull()
        assertThat(payment.approvedAt).isNull()
        assertThat(payment.canceledAt).isNull()
    }

    @Test
    @DisplayName("READY 상태의 결제를 시작하면 IN_PROGRESS 상태가 되고 paymentKey가 저장된다")
    fun startPayment() {
        val payment = createReadyPayment()
        payment.start("payment-key")

        assertThat(payment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
        assertThat(payment.paymentKey).isEqualTo("payment-key")
    }

    @Test
    @DisplayName("READY 상태가 아닌 결제를 시작하면 예외가 발생한다")
    fun startPaymentInvalidStatus() {
        val payment = createReadyPayment()
        payment.start("payment-key")

        assertThatThrownBy { payment.start("another-payment-key") }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    @DisplayName("IN_PROGRESS 상태의 결제를 완료하면 DONE 상태가 된다")
    fun completePayment() {
        val payment = createInProgressPayment()
        val approvedAt = LocalDateTime.now()
        payment.complete(approvedAt)

        assertThat(payment.status).isEqualTo(PaymentStatus.DONE)
        assertThat(payment.approvedAt).isEqualTo(approvedAt)
    }

    @Test
    @DisplayName("IN_PROGRESS 상태가 아닌 결제를 완료하면 예외가 발생한다")
    fun completePaymentInvalidStatus() {
        val payment = createReadyPayment()
        assertThatThrownBy { payment.complete(LocalDateTime.now()) }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    @DisplayName("IN_PROGRESS 상태의 결제를 실패 처리하면 FAILED 상태가 된다")
    fun failPayment() {
        val payment = createInProgressPayment()
        payment.fail()
        assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
    }

    @Test
    @DisplayName("IN_PROGRESS 상태가 아닌 결제를 실패 처리하면 예외가 발생한다")
    fun failPaymentInvalidStatus() {
        val payment = createReadyPayment()
        assertThatThrownBy { payment.fail() }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    @DisplayName("DONE 상태의 결제를 취소하면 CANCELED 상태가 된다")
    fun cancelPayment() {
        val payment = createDonePayment()
        payment.cancel()

        assertThat(payment.status).isEqualTo(PaymentStatus.CANCELED)
        assertThat(payment.canceledAt).isNotNull()
    }

    @Test
    @DisplayName("DONE 상태가 아닌 결제를 일반 취소하면 예외가 발생한다")
    fun cancelPaymentInvalidStatus() {
        val payment = createInProgressPayment()
        assertThatThrownBy { payment.cancel() }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    @DisplayName("승인 후 처리 실패 시 IN_PROGRESS 상태의 결제를 CANCELED 상태로 변경한다")
    fun cancelAfterApprovalFailure() {
        val payment = createInProgressPayment()
        payment.cancelAfterApprovalFailure()

        assertThat(payment.status).isEqualTo(PaymentStatus.CANCELED)
        assertThat(payment.canceledAt).isNotNull()
    }

    @Test
    @DisplayName("IN_PROGRESS 상태가 아닌 결제를 승인 실패 보상 취소하면 예외가 발생한다")
    fun cancelAfterApprovalFailureInvalidStatus() {
        val payment = createDonePayment()
        assertThatThrownBy { payment.cancelAfterApprovalFailure() }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    @DisplayName("READY 상태의 결제를 Webhook으로 완료하면 DONE 상태가 된다")
    fun completeByWebhookFromReady() {
        val payment = createReadyPayment()
        val approvedAt = LocalDateTime.now()
        payment.completeByWebhook("payment-key", approvedAt)

        assertThat(payment.status).isEqualTo(PaymentStatus.DONE)
        assertThat(payment.paymentKey).isEqualTo("payment-key")
        assertThat(payment.approvedAt).isEqualTo(approvedAt)
    }

    @Test
    @DisplayName("IN_PROGRESS 상태의 결제를 Webhook으로 완료하면 DONE 상태가 된다")
    fun completeByWebhookFromInProgress() {
        val payment = createInProgressPayment()
        val approvedAt = LocalDateTime.now()
        payment.completeByWebhook("payment-key", approvedAt)
        assertThat(payment.status).isEqualTo(PaymentStatus.DONE)
    }

    @Test
    @DisplayName("READY 상태의 결제를 Webhook으로 실패 처리하면 FAILED 상태가 된다")
    fun failByWebhookFromReady() {
        val payment = createReadyPayment()
        payment.failByWebhook()
        assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
    }

    @Test
    @DisplayName("READY 상태의 결제를 Webhook으로 취소하면 CANCELED 상태가 된다")
    fun cancelByWebhookFromReady() {
        val payment = createReadyPayment()
        payment.cancelByWebhook()

        assertThat(payment.status).isEqualTo(PaymentStatus.CANCELED)
        assertThat(payment.canceledAt).isNotNull()
    }

    @Test
    @DisplayName("DONE 상태의 결제를 Webhook으로 다시 완료하면 예외가 발생한다")
    fun completeByWebhookInvalidStatus() {
        val payment = createDonePayment()
        assertThatThrownBy { payment.completeByWebhook("payment-key", LocalDateTime.now()) }
            .isInstanceOf(BusinessException::class.java)
    }

    private fun createReadyPayment(): Payment =
        Payment.create(mock(Purchase::class.java), 15000)

    private fun createInProgressPayment(): Payment = createReadyPayment().also {
        it.start("payment-key")
    }

    private fun createDonePayment(): Payment = createInProgressPayment().also {
        it.complete(LocalDateTime.now())
    }
}
