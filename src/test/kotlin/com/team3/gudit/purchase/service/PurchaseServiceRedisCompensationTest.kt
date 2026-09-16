package com.team3.gudit.purchase.service

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.outbox.service.OutboxEventService
import com.team3.gudit.payment.entity.Payment
import com.team3.gudit.payment.entity.PaymentStatus
import com.team3.gudit.payment.exception.PaymentErrorCode
import com.team3.gudit.payment.service.PaymentService
import com.team3.gudit.purchase.entity.Purchase
import com.team3.gudit.purchase.entity.PurchaseStatus
import com.team3.gudit.purchase.exception.PurchaseErrorCode
import com.team3.gudit.purchase.repository.PurchaseRepository
import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.sale.domain.repository.SaleRepository
import com.team3.gudit.sale.metrics.InventoryMetrics
import com.team3.gudit.sale.service.InventoryService
import com.team3.gudit.user.domain.repository.UserRepository
import java.time.LocalDateTime
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PurchaseServiceRedisCompensationTest {

    @Mock
    private lateinit var purchaseRepository: PurchaseRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var saleRepository: SaleRepository

    @Mock
    private lateinit var inventoryService: InventoryService

    @Mock
    private lateinit var paymentService: PaymentService

    @Mock
    private lateinit var outboxEventService: OutboxEventService

    private lateinit var purchaseService: PurchaseService

    @Mock
    private lateinit var inventoryMetrics: InventoryMetrics

    private var userId: Long = 0
    private var saleId: Long = 0
    private var purchaseId: Long = 0

    @BeforeEach
    fun setUp() {
        purchaseService = PurchaseService(
            purchaseRepository,
            userRepository,
            saleRepository,
            inventoryService,
            paymentService,
            inventoryMetrics,
            outboxEventService
        )

        userId = 1L
        saleId = 10L
        purchaseId = 100L
    }

    @Test
    @DisplayName("PENDING_PAYMENT 구매 취소 시 결제를 취소하고 재고 복구 Outbox 이벤트를 저장한다")
    fun cancelPendingPayment() {
        // given
        val purchase = mock(Purchase::class.java)
        val sale = mock(Sale::class.java)

        given(purchaseRepository.findByIdAndUserIdWithLock(purchaseId, userId)).willReturn(Optional.of(purchase))

        given(purchase.status)
            .willReturn(PurchaseStatus.PENDING_PAYMENT, PurchaseStatus.PENDING_PAYMENT, PurchaseStatus.CANCELED)

        given(purchase.id)
            .willReturn(purchaseId)
        given(purchase.sale)
            .willReturn(sale)
        given(purchase.quantity)
            .willReturn(1)
        given(purchase.canceledAt)
            .willReturn(LocalDateTime.now())

        given(sale.id)
            .willReturn(saleId)
        given(sale.endAt)
            .willReturn(LocalDateTime.now().plusHours(1))

        val payment = Payment.create(purchase, 15_000)

        given(paymentService.getPaymentByPurchaseIdWithLock(purchaseId)).willReturn(payment)

        // when
        val response = purchaseService.cancel(userId, purchaseId)

        // then
        verify(paymentService)
            .getPaymentByPurchaseIdWithLock(purchaseId)

        verify(purchaseRepository)
            .findByIdAndUserIdWithLock(purchaseId, userId)

        assertThat(payment.status)
            .isEqualTo(PaymentStatus.CANCELED)

        verify(outboxEventService)
            .saveStockRestoreRequested(purchaseId, saleId, userId, 1)

        verify(inventoryService, never())
            .restoreStock(
                anyLong(),
                anyLong(),
                anyInt()
            )

        verify(purchase).cancel()

        assertThat(response.status)
            .isEqualTo(PurchaseStatus.CANCELED)
    }

    @Test
    @DisplayName("Payment가 IN_PROGRESS이면 사용자 취소와 재고 복구 Outbox 저장을 차단한다")
    fun cancelPendingPaymentWhenPaymentInProgress() {
        // given
        val purchase = mock(Purchase::class.java)
        val sale = mock(Sale::class.java)

        given(purchaseRepository.findByIdAndUserIdWithLock(purchaseId, userId)).willReturn(Optional.of(purchase))

        given(purchase.status)
            .willReturn(PurchaseStatus.PENDING_PAYMENT)

        given(purchase.sale)
            .willReturn(sale)

        given(sale.endAt)
            .willReturn(LocalDateTime.now().plusHours(1))

        val payment = Payment.create(purchase, 15_000)
        payment.start("payment-key")

        given(paymentService.getPaymentByPurchaseIdWithLock(purchaseId)).willReturn(payment)

        // when & then
        assertThatThrownBy {
            purchaseService.cancel(userId, purchaseId)
        }
            .isInstanceOf(BusinessException::class.java)
            .satisfies(java.util.function.Consumer<Throwable> { exception ->
                assertThat(
                    (exception as BusinessException)
                        .errorCode
                ).isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS)
            })

        assertThat(payment.status)
            .isEqualTo(PaymentStatus.IN_PROGRESS)

        verify(paymentService)
            .getPaymentByPurchaseIdWithLock(purchaseId)

        verify(outboxEventService, never())
            .saveStockRestoreRequested(
                anyLong(),
                anyLong(),
                anyLong(),
                anyInt()
            )

        verify(inventoryService, never())
            .restoreStock(
                anyLong(),
                anyLong(),
                anyInt()
            )

        verify(purchase, never()).cancel()
    }

    @Test
    @DisplayName("이미 취소된 Purchase는 다시 취소하거나 재고 복구 Outbox 이벤트를 저장하지 않는다")
    fun cancelAlreadyCanceledPurchase() {
        // given
        val purchase = mock(Purchase::class.java)
        val payment = mock(Payment::class.java)

        given(paymentService.getPaymentByPurchaseIdWithLock(purchaseId)).willReturn(payment)

        given(purchaseRepository.findByIdAndUserIdWithLock(purchaseId, userId)).willReturn(Optional.of(purchase))

        given(purchase.status)
            .willReturn(PurchaseStatus.CANCELED)

        // when & then
        assertThatThrownBy {
            purchaseService.cancel(userId, purchaseId)
        }
            .isInstanceOf(BusinessException::class.java)
            .satisfies(java.util.function.Consumer<Throwable> { exception ->
                assertThat(
                    (exception as BusinessException)
                        .errorCode
                ).isEqualTo(PurchaseErrorCode .PURCHASE_ALREADY_CANCELED)
            })

        verify(paymentService)
            .getPaymentByPurchaseIdWithLock(purchaseId)

        verify(outboxEventService, never())
            .saveStockRestoreRequested(
                anyLong(),
                anyLong(),
                anyLong(),
                anyInt()
            )

        verify(inventoryService, never())
            .restoreStock(
                anyLong(),
                anyLong(),
                anyInt()
            )

        verify(purchase, never()).cancel()
    }

    @Test
    @DisplayName("판매 종료 후 1일이 지나면 사용자 취소와 재고 복구 Outbox 저장을 차단한다")
    fun cancelAfterCancellationDeadline() {
        // given
        val purchase = mock(Purchase::class.java)
        val sale = mock(Sale::class.java)
        val payment = mock(Payment::class.java)

        given(paymentService.getPaymentByPurchaseIdWithLock(purchaseId)).willReturn(payment)

        given(purchaseRepository.findByIdAndUserIdWithLock(purchaseId, userId)).willReturn(Optional.of(purchase))

        given(purchase.status)
            .willReturn(PurchaseStatus.PENDING_PAYMENT)
        given(purchase.sale)
            .willReturn(sale)

        given(sale.endAt)
            .willReturn(LocalDateTime.now().minusDays(2))

        // when & then
        assertThatThrownBy {
            purchaseService.cancel(userId, purchaseId)
        }
            .isInstanceOf(BusinessException::class.java)
            .satisfies(java.util.function.Consumer<Throwable> { exception ->
                assertThat(
                    (exception as BusinessException)
                        .errorCode
                ).isEqualTo(PurchaseErrorCode .PURCHASE_CANCELLATION_PERIOD_EXPIRED)
            })

        verify(paymentService)
            .getPaymentByPurchaseIdWithLock(purchaseId)

        verify(outboxEventService, never())
            .saveStockRestoreRequested(
                anyLong(),
                anyLong(),
                anyLong(),
                anyInt()
            )

        verify(inventoryService, never())
            .restoreStock(
                anyLong(),
                anyLong(),
                anyInt()
            )

        verify(purchase, never()).cancel()
    }

    @Test
    @DisplayName("취소 유예기간 안의 PURCHASED 구매는 결제 취소 후 재고 복구 Outbox 이벤트를 저장한다")
    fun cancelPurchasedWithinCancellationPeriod() {
        // given
        val purchase = mock(Purchase::class.java)
        val sale = mock(Sale::class.java)
        val payment = mock(Payment::class.java)

        given(paymentService.getPaymentByPurchaseIdWithLock(purchaseId)).willReturn(payment)

        given(purchaseRepository.findByIdAndUserIdWithLock(purchaseId, userId)).willReturn(Optional.of(purchase))

        given(purchase.status)
            .willReturn(PurchaseStatus.PURCHASED)
        given(purchase.id)
            .willReturn(purchaseId)
        given(purchase.sale)
            .willReturn(sale)
        given(purchase.quantity)
            .willReturn(1)

        given(sale.id)
            .willReturn(saleId)
        given(sale.endAt)
            .willReturn(LocalDateTime.now().plusHours(1))

        given(payment.paymentKey)
            .willReturn("payment-key")

        // when
        purchaseService.cancel(userId, purchaseId)

        // then
        verify(paymentService)
            .getPaymentByPurchaseIdWithLock(purchaseId)

        verify(paymentService)
            .cancelCompletedPayment("payment-key")

        verify(outboxEventService)
            .saveStockRestoreRequested(purchaseId, saleId, userId, 1)

        verify(inventoryService, never())
            .restoreStock(
                anyLong(),
                anyLong(),
                anyInt()
            )

        verify(purchase).cancel()
    }
}
