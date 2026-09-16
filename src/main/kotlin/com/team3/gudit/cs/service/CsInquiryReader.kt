package com.team3.gudit.cs.service

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.payment.service.PaymentService
import com.team3.gudit.purchase.exception.PurchaseErrorCode
import com.team3.gudit.purchase.repository.PurchaseRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CsInquiryReader(
    private val purchaseRepository: PurchaseRepository,
    private val paymentService: PaymentService
) {
    @Transactional(readOnly = true)
    fun getOrderId(userId: Long?, purchaseId: Long?): String? {
        val purchase = purchaseRepository.findByIdAndUserId(purchaseId, userId)
            .orElseThrow {
                BusinessException(
                    PurchaseErrorCode.PURCHASE_NOT_FOUND,
                    "Purchase not found. purchaseId=$purchaseId"
                )
            }

        val payment = paymentService.getPaymentByPurchaseId(purchase.id)
        return payment.orderId
    }
}
