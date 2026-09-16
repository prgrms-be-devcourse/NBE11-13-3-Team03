package com.team3.gudit.cs.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.service.PaymentService;
import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.purchase.exception.PurchaseErrorCode;
import com.team3.gudit.purchase.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CsInquiryReader {

    private final PurchaseRepository purchaseRepository;
    private final PaymentService paymentService;

    @Transactional(readOnly = true)
    public String getOrderId(
            Long userId,
            Long purchaseId
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

        return payment.getOrderId();
    }
}