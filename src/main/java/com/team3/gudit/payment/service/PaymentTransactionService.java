package com.team3.gudit.payment.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.outbox.service.OutboxEventService;
import com.team3.gudit.payment.dto.TossPaymentResponse;
import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.entity.PaymentStatus;
import com.team3.gudit.payment.exception.PaymentErrorCode;
import com.team3.gudit.payment.repository.PaymentRepository;
import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import com.team3.gudit.purchase.exception.PurchaseErrorCode;
import com.team3.gudit.purchase.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;
    private final PurchaseRepository purchaseRepository;
    private final OutboxEventService outboxEventService;

    @Transactional
    public void startPayment(
            String orderId,
            String paymentKey,
            int amount
    ) {
        Payment payment = getPaymentByOrderIdWithLock(orderId);

        validateAmount(payment, amount);

        Purchase purchase = getLockedPurchase(payment);

        validatePendingPurchase(purchase);

        payment.start(paymentKey);
    }

    @Transactional
    public void completePayment(
            String orderId,
            TossPaymentResponse response
    ) {
        Payment payment = getPaymentByOrderIdWithLock(orderId);

        validatePaymentResponse(payment, response);

        Purchase purchase = getLockedPurchase(payment);

        validatePendingPurchase(purchase);

        payment.complete(
                response.approvedAt().toLocalDateTime()
        );

        purchase.complete();
    }

    @Transactional
    public void failPayment(String orderId) {
        Payment payment = getPaymentByOrderId(orderId);
        Purchase purchase = getLockedPurchase(payment);

        payment.fail();

        // 사용자 취소나 timeout이 이미 처리한 구매이면
        // 재고 복구 이벤트를 중복 생성하지 않는다.
        if (purchase.getStatus()
                != PurchaseStatus.PENDING_PAYMENT) {
            return;
        }

        purchase.cancel();

        saveStockRestoreRequested(purchase);
    }

    @Transactional
    public void compensateApprovalFailure(String paymentKey) {
        Payment payment = getPaymentByPaymentKey(paymentKey);
        Purchase purchase = getLockedPurchase(payment);

        payment.cancelAfterApprovalFailure();

        if (purchase.getStatus()
                != PurchaseStatus.PENDING_PAYMENT) {
            return;
        }

        purchase.cancel();

        saveStockRestoreRequested(purchase);
    }

    @Transactional
    public void completeCancel(String paymentKey) {
        Payment payment = getPaymentByPaymentKey(paymentKey);

        payment.cancel();
    }

    @Transactional(readOnly = true)
    public Payment getPaymentByOrderId(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() ->
                        new BusinessException(
                                PaymentErrorCode.PAYMENT_NOT_FOUND,
                                "Payment not found. orderId=" + orderId
                        )
                );
    }

    @Transactional(readOnly = true)
    public Payment getPaymentByPaymentKey(String paymentKey) {
        return paymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() ->
                        new BusinessException(
                                PaymentErrorCode.PAYMENT_NOT_FOUND,
                                "Payment not found. paymentKey=" + paymentKey
                        )
                );
    }

    private Payment getPaymentByOrderIdWithLock(String orderId) {
        return paymentRepository.findByOrderIdWithLock(orderId)
                .orElseThrow(() ->
                        new BusinessException(
                                PaymentErrorCode.PAYMENT_NOT_FOUND,
                                "Payment not found. orderId=" + orderId
                        )
                );
    }

    private void validateAmount(Payment payment, int amount) {
        if (payment.getAmount() != amount) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH
            );
        }

        if (payment.getPurchase().getPurchasePrice() != amount) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH
            );
        }
    }

    private void validatePaymentResponse(
            Payment payment,
            TossPaymentResponse response
    ) {
        if (!payment.getOrderId().equals(response.orderId())) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_ORDER_ID_MISMATCH
            );
        }

        if (payment.getAmount() != response.totalAmount()) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH
            );
        }
    }

    private Purchase getLockedPurchase(Payment payment) {
        return purchaseRepository
                .findByIdWithLock(
                        payment.getPurchase().getId()
                )
                .orElseThrow(() ->
                        new BusinessException(
                                PurchaseErrorCode.PURCHASE_NOT_FOUND
                        )
                );
    }

    private void validatePendingPurchase(
            Purchase purchase
    ) {
        if (purchase.getStatus()
                != PurchaseStatus.PENDING_PAYMENT) {

            throw new BusinessException(
                    PurchaseErrorCode.INVALID_PURCHASE_STATUS
            );
        }
    }

    @Transactional
    public void reconcileDone(TossPaymentResponse response) {
        Payment payment = getPaymentByOrderId(response.orderId());

        validatePaymentResponse(payment, response);

        Purchase purchase = getLockedPurchase(payment);

        if (payment.getStatus() == PaymentStatus.DONE) {
            return;
        }

        if (payment.getStatus() == PaymentStatus.CANCELED) {
            throw new BusinessException(
                    PaymentErrorCode.INVALID_PAYMENT_STATUS,
                    "Canceled payment received DONE webhook. orderId="
                            + response.orderId()
            );
        }

        validatePendingPurchase(purchase);

        payment.completeByWebhook(
                response.paymentKey(),
                response.approvedAt().toLocalDateTime()
        );

        purchase.complete();
    }

    @Transactional
    public void reconcileCanceled(TossPaymentResponse response) {
        Payment payment = getPaymentByOrderId(response.orderId());

        validatePaymentResponse(payment, response);

        Purchase purchase = getLockedPurchase(payment);

        if (payment.getStatus() == PaymentStatus.CANCELED) {
            return;
        }

        if (purchase.getStatus() != PurchaseStatus.PENDING_PAYMENT
                && purchase.getStatus() != PurchaseStatus.PURCHASED) {
            return;
        }

        payment.cancelByWebhook();
        purchase.cancel();

        saveStockRestoreRequested(purchase);
    }

    @Transactional
    public void reconcileAborted(TossPaymentResponse response) {
        Payment payment = getPaymentByOrderId(response.orderId());

        validatePaymentResponse(payment, response);

        Purchase purchase = getLockedPurchase(payment);

        if (payment.getStatus() == PaymentStatus.FAILED) {
            return;
        }

        if (payment.getStatus() == PaymentStatus.CANCELED
                || payment.getStatus() == PaymentStatus.DONE) {
            return;
        }

        if (purchase.getStatus()
                != PurchaseStatus.PENDING_PAYMENT) {
            return;
        }

        payment.failByWebhook();
        purchase.cancel();

        saveStockRestoreRequested(purchase);
    }

    @Transactional
    public void reconcileExpired(TossPaymentResponse response) {
        Payment payment = getPaymentByOrderId(response.orderId());

        validatePaymentResponse(payment, response);

        Purchase purchase = getLockedPurchase(payment);

        if (payment.getStatus() == PaymentStatus.CANCELED) {
            return;
        }

        if (payment.getStatus() == PaymentStatus.DONE
                || payment.getStatus() == PaymentStatus.FAILED) {
            return;
        }

        if (purchase.getStatus()
                != PurchaseStatus.PENDING_PAYMENT) {
            return;
        }

        payment.cancelByWebhook();
        purchase.cancel();

        saveStockRestoreRequested(purchase);
    }

    private void saveStockRestoreRequested(
            Purchase purchase
    ) {
        outboxEventService.saveStockRestoreRequested(
                purchase.getId(),
                purchase.getSale().getId(),
                purchase.getUser().getId(),
                purchase.getQuantity()
        );
    }
}