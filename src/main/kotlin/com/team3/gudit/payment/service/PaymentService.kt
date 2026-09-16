package com.team3.gudit.payment.service

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.payment.client.TossPaymentClient
import com.team3.gudit.payment.dto.PaymentConfirmRequest
import com.team3.gudit.payment.dto.PaymentStatusResult
import com.team3.gudit.payment.dto.TossPaymentCancelRequest
import com.team3.gudit.payment.dto.TossPaymentConfirmRequest
import com.team3.gudit.payment.dto.TossPaymentResponse
import com.team3.gudit.payment.entity.Payment
import com.team3.gudit.payment.exception.PaymentErrorCode
import com.team3.gudit.payment.exception.TossPaymentException
import com.team3.gudit.payment.repository.PaymentRepository
import com.team3.gudit.purchase.entity.Purchase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val tossPaymentClient: TossPaymentClient,
    private val paymentTransactionService: PaymentTransactionService
) {
    @Transactional
    fun createPayment(purchase: Purchase): Payment {
        val payment = Payment.create(purchase, purchase.purchasePrice)
        return paymentRepository.save(payment)
    }

    fun confirmPayment(
        paymentKey: String?,
        orderId: String?,
        amount: Int
    ): TossPaymentResponse {
        val request = TossPaymentConfirmRequest(paymentKey, orderId, amount)
        val idempotencyKey = "GUDIT_CONFIRM_$orderId"
        return tossPaymentClient.confirm(request, idempotencyKey)
    }

    fun cancelPayment(paymentKey: String?): TossPaymentResponse =
        cancelPayment(paymentKey, "구매 취소")

    private fun cancelPayment(
        paymentKey: String?,
        cancelReason: String
    ): TossPaymentResponse {
        val request = TossPaymentCancelRequest(cancelReason)
        val idempotencyKey = "GUDIT_CANCEL_$paymentKey"
        return tossPaymentClient.cancel(paymentKey, request, idempotencyKey)
    }

    fun confirm(request: PaymentConfirmRequest): TossPaymentResponse {
        paymentTransactionService.startPayment(request.orderId, request.paymentKey, request.amount)

        try {
            val response = confirmPayment(request.paymentKey, request.orderId, request.amount)
            try {
                paymentTransactionService.completePayment(request.orderId, response)
                return response
            } catch (exception: RuntimeException) {
                compensateAfterApproval(request.paymentKey)
                throw BusinessException(PaymentErrorCode.PAYMENT_FINALIZATION_FAILED, exception)
            }
        } catch (exception: TossPaymentException) {
            if (isDefinitePaymentFailure(exception.code)) {
                paymentTransactionService.failPayment(request.orderId)
                throw BusinessException(PaymentErrorCode.PAYMENT_CONFIRM_FAILED, exception)
            }
            return reconcilePayment(request, exception)
        }
    }

    fun getPayment(paymentKey: String?): TossPaymentResponse =
        tossPaymentClient.getPayment(paymentKey)

    @Transactional(readOnly = true)
    fun getStatus(orderId: String?): PaymentStatusResult {
        val payment = paymentRepository.findByOrderId(orderId).orElseThrow {
            BusinessException(
                PaymentErrorCode.PAYMENT_NOT_FOUND,
                "Payment not found. orderId=$orderId"
            )
        }
        val purchase = payment.purchase
        return PaymentStatusResult(
            payment.orderId,
            purchase.id,
            purchase.status,
            payment.status,
            payment.amount
        )
    }

    private fun reconcilePayment(
        request: PaymentConfirmRequest,
        originalException: TossPaymentException
    ): TossPaymentResponse {
        try {
            val response = tossPaymentClient.getPayment(request.paymentKey)
            if (response.status == "DONE") {
                try {
                    paymentTransactionService.completePayment(request.orderId, response)
                    return response
                } catch (exception: RuntimeException) {
                    compensateAfterApproval(request.paymentKey)
                    throw BusinessException(PaymentErrorCode.PAYMENT_FINALIZATION_FAILED, exception)
                }
            }
            throw BusinessException(PaymentErrorCode.PAYMENT_PROCESSING_ERROR, originalException)
        } catch (exception: TossPaymentException) {
            throw BusinessException(PaymentErrorCode.PAYMENT_PROCESSING_ERROR, originalException)
        }
    }

    private fun compensateAfterApproval(paymentKey: String?) {
        try {
            cancelPayment(paymentKey, "결제 승인 후 처리 실패 보상 취소")
            paymentTransactionService.compensateApprovalFailure(paymentKey)
        } catch (compensationException: RuntimeException) {
            paymentTransactionService.requestPaymentCompensation(paymentKey)
            throw BusinessException(
                PaymentErrorCode.PAYMENT_COMPENSATION_FAILED,
                compensationException
            )
        }
    }

    fun cancelCompletedPayment(paymentKey: String?) {
        cancelPayment(paymentKey)
        paymentTransactionService.completeCancel(paymentKey)
    }

    @Transactional(readOnly = true)
    fun getPaymentByPurchaseId(purchaseId: Long?): Payment =
        paymentRepository.findByPurchaseId(purchaseId).orElseThrow {
            BusinessException(
                PaymentErrorCode.PAYMENT_NOT_FOUND,
                "Payment not found. purchaseId=$purchaseId"
            )
        }

    @Transactional
    fun getPaymentByPurchaseIdWithLock(purchaseId: Long?): Payment =
        paymentRepository.findByPurchaseIdWithLock(purchaseId).orElseThrow {
            BusinessException(
                PaymentErrorCode.PAYMENT_NOT_FOUND,
                "Payment not found. purchaseId=$purchaseId"
            )
        }

    private fun isDefinitePaymentFailure(tossErrorCode: String?): Boolean = when (tossErrorCode) {
        "REJECT_ACCOUNT_PAYMENT",
        "REJECT_CARD_COMPANY",
        "INVALID_CARD_EXPIRATION",
        "INVALID_STOPPED_CARD",
        "INVALID_CARD_LOST_OR_STOLEN",
        "EXCEED_MAX_DAILY_PAYMENT_COUNT",
        "EXCEED_MAX_AMOUNT",
        "EXCEED_MAX_ONE_DAY_AMOUNT",
        "EXCEED_MAX_ONE_DAY_WITHDRAW_AMOUNT",
        "EXCEED_MAX_ONE_TIME_WITHDRAW_AMOUNT" -> true
        else -> false
    }
}
