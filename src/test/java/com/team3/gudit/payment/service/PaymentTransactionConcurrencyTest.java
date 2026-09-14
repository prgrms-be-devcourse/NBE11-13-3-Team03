package com.team3.gudit.payment.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.outbox.service.OutboxEventService;
import com.team3.gudit.payment.dto.TossPaymentResponse;
import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.entity.PaymentStatus;
import com.team3.gudit.payment.repository.PaymentRepository;
import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import com.team3.gudit.purchase.exception.PurchaseErrorCode;
import com.team3.gudit.purchase.repository.PurchaseRepository;
import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.user.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionConcurrencyTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private OutboxEventService outboxEventService;

    private PaymentTransactionService paymentTransactionService;

    private Long purchaseId;
    private Long saleId;
    private Long userId;

    @BeforeEach
    void setUp() {
        paymentTransactionService =
                new PaymentTransactionService(
                        paymentRepository,
                        purchaseRepository,
                        outboxEventService
                );

        purchaseId = 100L;
        saleId = 10L;
        userId = 1L;
    }

    @Test
    @DisplayName("결제 시작 전에 Purchase를 잠금 조회하고 PENDING_PAYMENT 상태를 재검증한다")
    void startPaymentWithLockedPurchase() {
        // given
        int amount = 15_000;
        String paymentKey = "payment-key";

        Purchase paymentPurchase = mock(Purchase.class);
        Purchase lockedPurchase = mock(Purchase.class);

        given(paymentPurchase.getId())
                .willReturn(purchaseId);
        given(paymentPurchase.getPurchasePrice())
                .willReturn(amount);

        given(lockedPurchase.getStatus())
                .willReturn(PurchaseStatus.PENDING_PAYMENT);

        Payment payment = Payment.create(
                paymentPurchase,
                amount
        );

        given(paymentRepository.findByOrderIdWithLock(
                payment.getOrderId()
        )).willReturn(Optional.of(payment));

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(lockedPurchase));

        // when
        paymentTransactionService.startPayment(
                payment.getOrderId(),
                paymentKey,
                amount
        );

        // then
        verify(paymentRepository)
                .findByOrderIdWithLock(
                        payment.getOrderId()
                );

        verify(purchaseRepository)
                .findByIdWithLock(purchaseId);

        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.IN_PROGRESS);

        assertThat(payment.getPaymentKey())
                .isEqualTo(paymentKey);
    }

    @Test
    @DisplayName("잠금 조회한 Purchase가 CANCELED이면 결제를 시작할 수 없다")
    void startPaymentWhenPurchaseCanceled() {
        // given
        int amount = 15_000;

        Purchase paymentPurchase = mock(Purchase.class);
        Purchase lockedPurchase = mock(Purchase.class);

        given(paymentPurchase.getId())
                .willReturn(purchaseId);
        given(paymentPurchase.getPurchasePrice())
                .willReturn(amount);

        given(lockedPurchase.getStatus())
                .willReturn(PurchaseStatus.CANCELED);

        Payment payment = Payment.create(
                paymentPurchase,
                amount
        );

        given(paymentRepository.findByOrderIdWithLock(
                payment.getOrderId()
        )).willReturn(Optional.of(payment));

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(lockedPurchase));

        // when & then
        assertThatThrownBy(() ->
                paymentTransactionService.startPayment(
                        payment.getOrderId(),
                        "payment-key",
                        amount
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                PurchaseErrorCode
                                        .INVALID_PURCHASE_STATUS
                        )
                );

        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("결제 완료 시 잠금 조회한 Purchase를 완료 처리한다")
    void completePaymentWithLockedPurchase() {
        // given
        int amount = 15_000;

        Purchase paymentPurchase = mock(Purchase.class);
        Purchase lockedPurchase = mock(Purchase.class);
        TossPaymentResponse response =
                mock(TossPaymentResponse.class);

        given(paymentPurchase.getId())
                .willReturn(purchaseId);

        given(lockedPurchase.getStatus())
                .willReturn(PurchaseStatus.PENDING_PAYMENT);

        Payment payment = Payment.create(
                paymentPurchase,
                amount
        );

        payment.start("payment-key");

        given(paymentRepository.findByOrderIdWithLock(
                payment.getOrderId()
        )).willReturn(Optional.of(payment));

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(lockedPurchase));

        given(response.orderId())
                .willReturn(payment.getOrderId());

        given(response.totalAmount())
                .willReturn(amount);

        given(response.approvedAt())
                .willReturn(
                        OffsetDateTime.parse(
                                "2026-08-19T10:00:00+09:00"
                        )
                );

        // when
        paymentTransactionService.completePayment(
                payment.getOrderId(),
                response
        );

        // then
        verify(paymentRepository)
                .findByOrderIdWithLock(
                        payment.getOrderId()
                );

        verify(purchaseRepository)
                .findByIdWithLock(purchaseId);

        verify(lockedPurchase).complete();

        verify(paymentPurchase, never()).complete();

        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.DONE);
    }

    @Test
    @DisplayName("결제 실패 시 PENDING_PAYMENT Purchase만 취소하고 재고 복구 Outbox 이벤트를 저장한다")
    void failPaymentWithPendingPurchase() {
        // given
        Purchase paymentPurchase = mock(Purchase.class);
        Purchase lockedPurchase = mock(Purchase.class);
        Sale sale = mock(Sale.class);
        User user = mock(User.class);

        given(paymentPurchase.getId())
                .willReturn(purchaseId);

        given(lockedPurchase.getId())
                .willReturn(purchaseId);

        given(lockedPurchase.getStatus())
                .willReturn(PurchaseStatus.PENDING_PAYMENT);

        given(lockedPurchase.getSale())
                .willReturn(sale);

        given(lockedPurchase.getUser())
                .willReturn(user);

        given(lockedPurchase.getQuantity())
                .willReturn(1);

        given(sale.getId())
                .willReturn(saleId);

        given(user.getId())
                .willReturn(userId);

        Payment payment = Payment.create(
                paymentPurchase,
                15_000
        );

        payment.start("payment-key");

        given(paymentRepository.findByOrderId(
                payment.getOrderId()
        )).willReturn(Optional.of(payment));

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(lockedPurchase));

        // when
        paymentTransactionService.failPayment(
                payment.getOrderId()
        );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.FAILED);

        verify(outboxEventService)
                .saveStockRestoreRequested(
                        purchaseId,
                        saleId,
                        userId,
                        1
                );

        verify(lockedPurchase).cancel();
        verify(paymentPurchase, never()).cancel();
    }

    @Test
    @DisplayName("결제 실패 시 Purchase가 이미 CANCELED이면 재고 복구 Outbox 이벤트를 중복 저장하지 않는다")
    void failPaymentWhenPurchaseAlreadyCanceled() {
        // given
        Purchase paymentPurchase = mock(Purchase.class);
        Purchase lockedPurchase = mock(Purchase.class);

        given(paymentPurchase.getId())
                .willReturn(purchaseId);

        given(lockedPurchase.getStatus())
                .willReturn(PurchaseStatus.CANCELED);

        Payment payment = Payment.create(
                paymentPurchase,
                15_000
        );

        payment.start("payment-key");

        given(paymentRepository.findByOrderId(
                payment.getOrderId()
        )).willReturn(Optional.of(payment));

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(lockedPurchase));

        // when
        paymentTransactionService.failPayment(
                payment.getOrderId()
        );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.FAILED);

        verify(outboxEventService, never())
                .saveStockRestoreRequested(
                        anyLong(),
                        anyLong(),
                        anyLong(),
                        anyInt()
                );

        verify(lockedPurchase, never()).cancel();
    }

    @Test
    @DisplayName("승인 실패 보상 시 Purchase가 이미 CANCELED이면 재고 복구 Outbox 이벤트를 중복 저장하지 않는다")
    void compensateApprovalFailureWhenPurchaseAlreadyCanceled() {
        // given
        Purchase paymentPurchase = mock(Purchase.class);
        Purchase lockedPurchase = mock(Purchase.class);

        given(paymentPurchase.getId())
                .willReturn(purchaseId);

        given(lockedPurchase.getStatus())
                .willReturn(PurchaseStatus.CANCELED);

        Payment payment = Payment.create(
                paymentPurchase,
                15_000
        );

        payment.start("payment-key");

        given(paymentRepository.findByPaymentKey(
                "payment-key"
        )).willReturn(Optional.of(payment));

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(lockedPurchase));

        // when
        paymentTransactionService
                .compensateApprovalFailure(
                        "payment-key"
                );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.CANCELED);

        verify(outboxEventService, never())
                .saveStockRestoreRequested(
                        anyLong(),
                        anyLong(),
                        anyLong(),
                        anyInt()
                );

        verify(lockedPurchase, never()).cancel();
    }
}