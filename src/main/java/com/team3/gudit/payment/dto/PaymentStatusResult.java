package com.team3.gudit.payment.dto;

import com.team3.gudit.payment.entity.PaymentStatus;
import com.team3.gudit.purchase.entity.PurchaseStatus;

public record PaymentStatusResult(
        String orderId,
        Long purchaseId,
        PurchaseStatus purchaseStatus,
        PaymentStatus paymentStatus,
        int amount
) {
}