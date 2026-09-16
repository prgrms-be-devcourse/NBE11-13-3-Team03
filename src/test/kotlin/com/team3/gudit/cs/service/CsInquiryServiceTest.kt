package com.team3.gudit.cs.service

import com.team3.gudit.cs.client.CsWorkflowClient
import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.purchase.exception.PurchaseErrorCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class CsInquiryServiceTest {
    @Mock
    private lateinit var csInquiryReader: CsInquiryReader

    @Mock
    private lateinit var csWorkflowClient: CsWorkflowClient

    @InjectMocks
    private lateinit var csInquiryService: CsInquiryService

    @Test
    @DisplayName("구매의 주문번호로 CS 워크플로우에 문의를 전달한다")
    fun submitInquiry() {
        // given
        val userId = 1L
        val purchaseId = 100L
        val orderId = "GUDIT_test-order-id"
        val message = "결제 상태를 확인해주세요."

        given(csInquiryReader.getOrderId(userId, purchaseId))
            .willReturn(orderId)

        // when
        csInquiryService.submitInquiry(userId, purchaseId, message)

        // then
        verify(csInquiryReader).getOrderId(userId, purchaseId)
        verify(csWorkflowClient).sendInquiry(orderId, message)
    }

    @Test
    @DisplayName("구매 조회에 실패하면 CS 워크플로우를 호출하지 않는다")
    fun submitInquiryPurchaseNotFound() {
        // given
        val userId = 1L
        val purchaseId = 100L
        val message = "결제 상태를 확인해주세요."

        given(csInquiryReader.getOrderId(userId, purchaseId))
            .willThrow(BusinessException(PurchaseErrorCode.PURCHASE_NOT_FOUND))

        // when & then
        assertThatThrownBy {
            csInquiryService.submitInquiry(userId, purchaseId, message)
        }
            .isInstanceOf(BusinessException::class.java)

        verify(csWorkflowClient, never()).sendInquiry(anyString(), anyString())
    }
}
