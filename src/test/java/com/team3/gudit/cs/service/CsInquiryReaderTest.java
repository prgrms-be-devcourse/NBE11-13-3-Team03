package com.team3.gudit.cs.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.service.PaymentService;
import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.purchase.exception.PurchaseErrorCode;
import com.team3.gudit.purchase.repository.PurchaseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CsInquiryReaderTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private CsInquiryReader csInquiryReader;

    @Test
    @DisplayName("사용자 구매 정보로 주문번호를 조회한다")
    void getOrderId() {
        // given
        Long userId = 1L;
        Long purchaseId = 100L;
        String orderId = "GUDIT_test-order-id";

        Purchase purchase = mock(Purchase.class);
        Payment payment = mock(Payment.class);

        given(purchaseRepository.findByIdAndUserId(
                purchaseId,
                userId
        ))
                .willReturn(Optional.of(purchase));

        given(purchase.getId())
                .willReturn(purchaseId);

        given(paymentService.getPaymentByPurchaseId(
                purchaseId
        ))
                .willReturn(payment);

        given(payment.getOrderId())
                .willReturn(orderId);

        // when
        String result =
                csInquiryReader.getOrderId(
                        userId,
                        purchaseId
                );

        // then
        assertThat(result)
                .isEqualTo(orderId);

        verify(purchaseRepository)
                .findByIdAndUserId(
                        purchaseId,
                        userId
                );

        verify(paymentService)
                .getPaymentByPurchaseId(purchaseId);
    }

    @Test
    @DisplayName("사용자의 구매 정보가 없으면 예외가 발생한다")
    void getOrderIdPurchaseNotFound() {
        // given
        Long userId = 1L;
        Long purchaseId = 100L;

        given(purchaseRepository.findByIdAndUserId(
                purchaseId,
                userId
        ))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> csInquiryReader.getOrderId(
                        userId,
                        purchaseId
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(
                            businessException.getErrorCode()
                    )
                            .isEqualTo(
                                    PurchaseErrorCode.PURCHASE_NOT_FOUND
                            );
                });

        verify(paymentService, never())
                .getPaymentByPurchaseId(purchaseId);
    }
}