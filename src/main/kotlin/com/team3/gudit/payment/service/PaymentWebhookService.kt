package com.team3.gudit.payment.service

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.payment.client.TossPaymentClient
import com.team3.gudit.payment.dto.TossPaymentResponse
import com.team3.gudit.payment.dto.TossPaymentWebhookRequest
import com.team3.gudit.payment.entity.PaymentStatus
import com.team3.gudit.payment.exception.PaymentErrorCode
import org.springframework.stereotype.Service

@Service
class PaymentWebhookService(
    private val tossPaymentClient: TossPaymentClient,
    private val paymentService: PaymentService,
    private val paymentTransactionService: PaymentTransactionService
) {
    fun handle(request: TossPaymentWebhookRequest) {
        if (request.eventType != PAYMENT_STATUS_CHANGED) {
            return
        }
        val webhookPayment = request.data ?: throw NullPointerException()
        val actualPayment = tossPaymentClient.getPayment(webhookPayment.paymentKey)
        validatePayment(webhookPayment, actualPayment)

        when (actualPayment.status) {
            "DONE" -> handleDone(actualPayment)
            "CANCELED" -> paymentTransactionService.reconcileCanceled(actualPayment)
            "ABORTED" -> paymentTransactionService.reconcileAborted(actualPayment)
            "EXPIRED" -> paymentTransactionService.reconcileExpired(actualPayment)
            else -> Unit
        }
    }

    private fun handleDone(actualPayment: TossPaymentResponse) {
        val payment = paymentTransactionService.getPaymentByOrderId(actualPayment.orderId)
        if (payment.status == PaymentStatus.CANCELED) {
            paymentService.cancelPayment(actualPayment.paymentKey)
            return
        }
        paymentTransactionService.reconcileDone(actualPayment)
    }

    private fun validatePayment(
        webhookPayment: TossPaymentResponse,
        actualPayment: TossPaymentResponse
    ) {
        if (webhookPayment.paymentKey != actualPayment.paymentKey) {
            throw BusinessException(
                PaymentErrorCode.PAYMENT_WEBHOOK_VALIDATION_FAILED,
                "Webhook paymentKey mismatch. paymentKey=${webhookPayment.paymentKey}"
            )
        }
        if (webhookPayment.orderId != actualPayment.orderId) {
            throw BusinessException(
                PaymentErrorCode.PAYMENT_WEBHOOK_VALIDATION_FAILED,
                "Webhook orderId mismatch. orderId=${webhookPayment.orderId}"
            )
        }
        if (webhookPayment.totalAmount != actualPayment.totalAmount) {
            throw BusinessException(
                PaymentErrorCode.PAYMENT_WEBHOOK_VALIDATION_FAILED,
                "Webhook amount mismatch. orderId=${webhookPayment.orderId}"
            )
        }
    }

    private companion object {
        const val PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED"
    }
}
