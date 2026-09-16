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
import com.team3.gudit.purchase.exception.PurchaseErrorCode
import com.team3.gudit.purchase.repository.PurchaseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentTransactionService(
    private val paymentRepository: PaymentRepository,
    private val purchaseRepository: PurchaseRepository,
    private val outboxEventService: OutboxEventService
) {
    @Transactional
    fun startPayment(orderId: String?, paymentKey: String?, amount: Int) {
        val payment = getPaymentByOrderIdWithLock(orderId)
        validateAmount(payment, amount)
        val purchase = getLockedPurchase(payment)
        validatePendingPurchase(purchase)
        payment.start(paymentKey)
    }

    @Transactional
    fun completePayment(orderId: String?, response: TossPaymentResponse) {
        val payment = getPaymentByOrderIdWithLock(orderId)
        validatePaymentResponse(payment, response)
        val purchase = getLockedPurchase(payment)
        validatePendingPurchase(purchase)
        payment.complete(
            response.approvedAt?.toLocalDateTime() ?: throw NullPointerException()
        )
        purchase.complete()
    }

    @Transactional
    fun failPayment(orderId: String?) {
        val payment = getPaymentByOrderId(orderId)
        val purchase = getLockedPurchase(payment)
        payment.fail()

        if (purchase.status != PurchaseStatus.PENDING_PAYMENT) {
            return
        }
        purchase.cancel()
        saveStockRestoreRequested(purchase)
    }

    @Transactional
    fun compensateApprovalFailure(paymentKey: String?) {
        val payment = getPaymentByPaymentKey(paymentKey)
        val purchase = getLockedPurchase(payment)

        if (payment.status == PaymentStatus.CANCELED) {
            return
        }
        payment.cancelAfterApprovalFailure()

        if (purchase.status != PurchaseStatus.PENDING_PAYMENT) {
            return
        }
        purchase.cancel()
        saveStockRestoreRequested(purchase)
    }

    @Transactional
    fun completeCancel(paymentKey: String?) {
        val payment = getPaymentByPaymentKey(paymentKey)
        payment.cancel()
    }

    @Transactional(readOnly = true)
    fun getPaymentByOrderId(orderId: String?): Payment =
        paymentRepository.findByOrderId(orderId).orElseThrow {
            BusinessException(
                PaymentErrorCode.PAYMENT_NOT_FOUND,
                "Payment not found. orderId=$orderId"
            )
        }

    @Transactional(readOnly = true)
    fun getPaymentByPaymentKey(paymentKey: String?): Payment =
        paymentRepository.findByPaymentKey(paymentKey).orElseThrow {
            BusinessException(
                PaymentErrorCode.PAYMENT_NOT_FOUND,
                "Payment not found. paymentKey=$paymentKey"
            )
        }

    private fun getPaymentByOrderIdWithLock(orderId: String?): Payment =
        paymentRepository.findByOrderIdWithLock(orderId).orElseThrow {
            BusinessException(
                PaymentErrorCode.PAYMENT_NOT_FOUND,
                "Payment not found. orderId=$orderId"
            )
        }

    private fun validateAmount(payment: Payment, amount: Int) {
        if (payment.amount != amount) {
            throw BusinessException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH)
        }
        if (payment.purchase.purchasePrice != amount) {
            throw BusinessException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH)
        }
    }

    private fun validatePaymentResponse(payment: Payment, response: TossPaymentResponse) {
        if (payment.orderId != response.orderId) {
            throw BusinessException(PaymentErrorCode.PAYMENT_ORDER_ID_MISMATCH)
        }
        if (payment.amount != response.totalAmount) {
            throw BusinessException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH)
        }
    }

    private fun getLockedPurchase(payment: Payment): Purchase =
        purchaseRepository.findByIdWithLock(payment.purchase.id).orElseThrow {
            BusinessException(PurchaseErrorCode.PURCHASE_NOT_FOUND)
        }

    private fun validatePendingPurchase(purchase: Purchase) {
        if (purchase.status != PurchaseStatus.PENDING_PAYMENT) {
            throw BusinessException(PurchaseErrorCode.INVALID_PURCHASE_STATUS)
        }
    }

    @Transactional
    fun reconcileDone(response: TossPaymentResponse) {
        val payment = getPaymentByOrderId(response.orderId)
        validatePaymentResponse(payment, response)
        val purchase = getLockedPurchase(payment)

        if (payment.status == PaymentStatus.DONE) {
            return
        }
        if (payment.status == PaymentStatus.CANCELED) {
            throw BusinessException(
                PaymentErrorCode.INVALID_PAYMENT_STATUS,
                "Canceled payment received DONE webhook. orderId=${response.orderId}"
            )
        }
        validatePendingPurchase(purchase)
        payment.completeByWebhook(
            response.paymentKey,
            response.approvedAt?.toLocalDateTime() ?: throw NullPointerException()
        )
        purchase.complete()
    }

    @Transactional
    fun reconcileCanceled(response: TossPaymentResponse) {
        val payment = getPaymentByOrderId(response.orderId)
        validatePaymentResponse(payment, response)
        val purchase = getLockedPurchase(payment)

        if (payment.status == PaymentStatus.CANCELED) {
            return
        }
        if (purchase.status != PurchaseStatus.PENDING_PAYMENT &&
            purchase.status != PurchaseStatus.PURCHASED
        ) {
            return
        }
        payment.cancelByWebhook()
        purchase.cancel()
        saveStockRestoreRequested(purchase)
    }

    @Transactional
    fun reconcileAborted(response: TossPaymentResponse) {
        val payment = getPaymentByOrderId(response.orderId)
        validatePaymentResponse(payment, response)
        val purchase = getLockedPurchase(payment)

        if (payment.status == PaymentStatus.FAILED) {
            return
        }
        if (payment.status == PaymentStatus.CANCELED || payment.status == PaymentStatus.DONE) {
            return
        }
        if (purchase.status != PurchaseStatus.PENDING_PAYMENT) {
            return
        }
        payment.failByWebhook()
        purchase.cancel()
        saveStockRestoreRequested(purchase)
    }

    @Transactional
    fun reconcileExpired(response: TossPaymentResponse) {
        val payment = getPaymentByOrderId(response.orderId)
        validatePaymentResponse(payment, response)
        val purchase = getLockedPurchase(payment)

        if (payment.status == PaymentStatus.CANCELED) {
            return
        }
        if (payment.status == PaymentStatus.DONE || payment.status == PaymentStatus.FAILED) {
            return
        }
        if (purchase.status != PurchaseStatus.PENDING_PAYMENT) {
            return
        }
        payment.cancelByWebhook()
        purchase.cancel()
        saveStockRestoreRequested(purchase)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun requestPaymentCompensation(paymentKey: String?) {
        val payment = getPaymentByPaymentKey(paymentKey)
        outboxEventService.savePaymentCompensationRequired(
            payment.id,
            payment.orderId,
            payment.paymentKey
        )
    }

    private fun saveStockRestoreRequested(purchase: Purchase) {
        outboxEventService.saveStockRestoreRequested(
            purchase.id,
            purchase.sale.id,
            purchase.user.id,
            purchase.quantity
        )
    }
}
