package com.team3.gudit.cs.service;

import com.team3.gudit.cs.client.CsWorkflowClient;
import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.purchase.exception.PurchaseErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CsInquiryServiceTest {

    @Mock
    private CsInquiryReader csInquiryReader;

    @Mock
    private CsWorkflowClient csWorkflowClient;

    @InjectMocks
    private CsInquiryService csInquiryService;

    @Test
    @DisplayName("구매의 주문번호로 CS 워크플로우에 문의를 전달한다")
    void submitInquiry() {
        // given
        Long userId = 1L;
        Long purchaseId = 100L;
        String orderId = "GUDIT_test-order-id";
        String message = "결제 상태를 확인해주세요.";

        given(csInquiryReader.getOrderId(
                userId,
                purchaseId
        ))
                .willReturn(orderId);

        // when
        csInquiryService.submitInquiry(
                userId,
                purchaseId,
                message
        );

        // then
        verify(csInquiryReader)
                .getOrderId(
                        userId,
                        purchaseId
                );

        verify(csWorkflowClient)
                .sendInquiry(
                        orderId,
                        message
                );
    }

    @Test
    @DisplayName("구매 조회에 실패하면 CS 워크플로우를 호출하지 않는다")
    void submitInquiryPurchaseNotFound() {
        // given
        Long userId = 1L;
        Long purchaseId = 100L;
        String message = "결제 상태를 확인해주세요.";

        given(csInquiryReader.getOrderId(
                userId,
                purchaseId
        ))
                .willThrow(
                        new BusinessException(
                                PurchaseErrorCode.PURCHASE_NOT_FOUND
                        )
                );

        // when & then
        assertThatThrownBy(
                () -> csInquiryService.submitInquiry(
                        userId,
                        purchaseId,
                        message
                )
        )
                .isInstanceOf(BusinessException.class);

        verify(csWorkflowClient, never())
                .sendInquiry(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString()
                );
    }
}