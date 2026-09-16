package com.team3.gudit.cs.service

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.payment.entity.Payment
import com.team3.gudit.payment.service.PaymentService
import com.team3.gudit.purchase.entity.Purchase
import com.team3.gudit.purchase.exception.PurchaseErrorCode
import com.team3.gudit.purchase.repository.PurchaseRepository
import java.util.Optional
import java.util.function.Consumer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class CsInquiryReaderTest {
    @Mock
    private lateinit var purchaseRepository: PurchaseRepository

    @Mock
    private lateinit var paymentService: PaymentService

    @InjectMocks
    private lateinit var csInquiryReader: CsInquiryReader

    @Test
    @DisplayName("사용자 구매 정보로 주문번호를 조회한다")
    fun getOrderId() {
        // given
        val userId = 1L
        val purchaseId = 100L
        val orderId = "GUDIT_test-order-id"
        val purchase = mock(Purchase::class.java)
        val payment = mock(Payment::class.java)

        given(purchaseRepository.findByIdAndUserId(purchaseId, userId))
            .willReturn(Optional.of(purchase))
        given(purchase.id).willReturn(purchaseId)
        given(paymentService.getPaymentByPurchaseId(purchaseId)).willReturn(payment)
        given(payment.orderId).willReturn(orderId)

        // when
        val result = csInquiryReader.getOrderId(userId, purchaseId)

        // then
        assertThat(result).isEqualTo(orderId)

        verify(purchaseRepository).findByIdAndUserId(purchaseId, userId)
        verify(paymentService).getPaymentByPurchaseId(purchaseId)
    }

    @Test
    @DisplayName("사용자의 구매 정보가 없으면 예외가 발생한다")
    fun getOrderIdPurchaseNotFound() {
        // given
        val userId = 1L
        val purchaseId = 100L

        given(purchaseRepository.findByIdAndUserId(purchaseId, userId))
            .willReturn(Optional.empty())

        // when & then
        assertThatThrownBy {
            csInquiryReader.getOrderId(userId, purchaseId)
        }
            .isInstanceOf(BusinessException::class.java)
            .satisfies(Consumer<Throwable> { exception ->
                val businessException = exception as BusinessException
                assertThat(businessException.errorCode)
                    .isEqualTo(PurchaseErrorCode.PURCHASE_NOT_FOUND)
            })

        verify(paymentService, never()).getPaymentByPurchaseId(purchaseId)
    }
}
