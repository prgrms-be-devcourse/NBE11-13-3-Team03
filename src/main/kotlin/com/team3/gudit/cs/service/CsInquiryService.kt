package com.team3.gudit.cs.service

import com.team3.gudit.cs.client.CsWorkflowClient
import org.springframework.stereotype.Service

@Service
class CsInquiryService(
    private val csInquiryReader: CsInquiryReader,
    private val csWorkflowClient: CsWorkflowClient
) {
    fun submitInquiry(userId: Long?, purchaseId: Long?, message: String?) {
        val orderId = csInquiryReader.getOrderId(userId, purchaseId)
        csWorkflowClient.sendInquiry(orderId, message)
    }
}
