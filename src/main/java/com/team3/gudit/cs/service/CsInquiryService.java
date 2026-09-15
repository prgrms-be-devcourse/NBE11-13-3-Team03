package com.team3.gudit.cs.service;

import com.team3.gudit.cs.client.CsWorkflowClient;
import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.service.PaymentService;
import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.purchase.exception.PurchaseErrorCode;
import com.team3.gudit.purchase.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CsInquiryService {

    private final PurchaseRepository purchaseRepository;
    private final PaymentService paymentService;
    private final CsWorkflowClient csWorkflowClient;

    public void submitInquiry(
            Long userId,
            Long purchaseId,
            String message
    ) {
        Purchase purchase = purchaseRepository
                .findByIdAndUserId(purchaseId, userId)
                .orElseThrow(() ->
                        new BusinessException(
                                PurchaseErrorCode.PURCHASE_NOT_FOUND,
                                "Purchase not found. purchaseId=" + purchaseId
                        )
                );

        Payment payment =
                paymentService.getPaymentByPurchaseId(
                        purchase.getId()
                );

        String orderId = payment.getOrderId();

        csWorkflowClient.sendInquiry(
                orderId,
                message
        );
    }
}