package com.team3.gudit.purchase.service

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.goods.domain.entity.Goods
import com.team3.gudit.outbox.service.OutboxEventService
import com.team3.gudit.payment.entity.Payment
import com.team3.gudit.payment.entity.PaymentStatus
import com.team3.gudit.payment.service.PaymentService
import com.team3.gudit.purchase.entity.Purchase
import com.team3.gudit.purchase.entity.PurchaseStatus
import com.team3.gudit.purchase.exception.PurchaseErrorCode
import com.team3.gudit.purchase.repository.PurchaseRepository
import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.sale.domain.repository.SaleRepository
import com.team3.gudit.sale.metrics.InventoryMetrics
import com.team3.gudit.sale.service.InventoryService
import com.team3.gudit.user.domain.entity.User
import com.team3.gudit.user.domain.repository.UserRepository
import java.time.LocalDateTime
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PurchaseServiceTest {

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
    private lateinit var inventoryMetrics: InventoryMetrics

    @Mock
    private lateinit var outboxEventService: OutboxEventService

    @InjectMocks
    private lateinit var purchaseService: PurchaseService

    private var userId: Long = 0
    private var saleId: Long = 0
    private var purchaseId: Long = 0

    @BeforeEach
    fun setUp() {
        userId = 1L
        saleId = 10L
        purchaseId = 100L
    }

    @Test
    @DisplayName("이미 구매한 판매 상품을 다시 구매하면 예외가 발생한다")
    fun purchaseDuplicate() {
        // given
        given(purchaseRepository.existsByUserIdAndSaleIdAndStatusNot(userId, saleId, PurchaseStatus.CANCELED))
            .willReturn(true)

        // when & then
        assertThatThrownBy {
            purchaseService.purchase(userId, saleId)
        }
            .isInstanceOf(BusinessException::class.java)
            .satisfies(java.util.function.Consumer<Throwable> { exception ->
                val businessException = exception as BusinessException

                assertThat(businessException.errorCode)
                    .isEqualTo(PurchaseErrorCode.DUPLICATE_PURCHASE)
            })

        verify(purchaseRepository)
            .existsByUserIdAndSaleIdAndStatusNot(userId, saleId, PurchaseStatus.CANCELED)
    }

    @Test
    @DisplayName("존재하지 않는 구매 내역을 조회하면 예외가 발생한다")
    fun getPurchaseNotFound() {
        // given
        given(purchaseRepository.findByIdAndUserId(purchaseId, userId))
            .willReturn(Optional.empty())

        // when & then
        assertThatThrownBy {
            purchaseService.getPurchase(userId, purchaseId)
        }
            .isInstanceOf(BusinessException::class.java)
            .satisfies(java.util.function.Consumer<Throwable> { exception ->
                val businessException = exception as BusinessException

                assertThat(businessException.errorCode)
                    .isEqualTo(PurchaseErrorCode.PURCHASE_NOT_FOUND)
            })
    }

    @Test
    @DisplayName("이미 취소된 구매를 다시 취소하면 예외가 발생한다")
    fun cancelAlreadyCanceledPurchase() {
        // given
        val lockedPurchase = mock(Purchase::class.java)
        val payment = mock(Payment::class.java)

        given(paymentService.getPaymentByPurchaseIdWithLock(purchaseId))
            .willReturn(payment)

        given(purchaseRepository.findByIdAndUserIdWithLock(purchaseId, userId))
            .willReturn(Optional.of(lockedPurchase))

        given(lockedPurchase.status)
            .willReturn(PurchaseStatus.CANCELED)

        // when & then
        assertThatThrownBy {
            purchaseService.cancel(userId, purchaseId)
        }
            .isInstanceOf(BusinessException::class.java)
            .satisfies(java.util.function.Consumer<Throwable> { exception ->
                val businessException = exception as BusinessException

                assertThat(businessException.errorCode)
                    .isEqualTo(PurchaseErrorCode .PURCHASE_ALREADY_CANCELED)
            })

        verify(paymentService)
            .getPaymentByPurchaseIdWithLock(purchaseId)

        verify(purchaseRepository)
            .findByIdAndUserIdWithLock(purchaseId, userId)

        verify(outboxEventService, never())
            .saveStockRestoreRequested(
                any(),
                anyLong(),
                anyLong(),
                anyInt()
            )
    }

    @Test
    @DisplayName("판매 상품 구매를 요청하면 재고를 차감하고 결제 대기 상태의 구매와 결제를 생성한다")
    fun purchaseSuccess() {
        // given
        val user = mock(User::class.java)
        val sale = mock(Sale::class.java)
        val goods = mock(Goods::class.java)
        val payment = mock(Payment::class.java)

        given(purchaseRepository.existsByUserIdAndSaleIdAndStatusNot(userId, saleId, PurchaseStatus.CANCELED))
            .willReturn(false)

        given(userRepository.findById(userId))
            .willReturn(Optional.of(user))

        given(saleRepository.findById(saleId))
            .willReturn(Optional.of(sale))

        given(sale.id)
            .willReturn(saleId)

        given(sale.goods)
            .willReturn(goods)

        given(goods.price)
            .willReturn(15000)

        given(purchaseRepository.save(anyValue(Purchase::class.java)))
            .willAnswer { invocation -> invocation.getArgument<Purchase>(0)
            }

        given(paymentService.createPayment(anyValue(Purchase::class.java)))
            .willReturn(payment)

        given(payment.orderId)
            .willReturn("GUDIT_test-order-id")

        // when
        val response = purchaseService.purchase(userId, saleId)

        // then
        assertThat(response.saleId)
            .isEqualTo(saleId)

        assertThat(response.quantity)
            .isEqualTo(1)

        assertThat(response.purchasePrice)
            .isEqualTo(15000)

        assertThat(response.status)
            .isEqualTo(PurchaseStatus.PENDING_PAYMENT)

        assertThat(response.purchasedAt)
            .isNull()

        assertThat(response.orderId)
            .isEqualTo("GUDIT_test-order-id")

        verify(inventoryService)
            .decreaseStock(saleId, userId, 1)

        verify(purchaseRepository)
            .save(anyValue(Purchase::class.java))

        verify(paymentService)
            .createPayment(anyValue(Purchase::class.java))
    }

    @Test
    @DisplayName("결제 대기 중인 구매를 취소하면 READY 결제를 취소하고 재고를 복구한다")
    fun cancelPendingPaymentSuccess() {
        // given
        val user = mock(User::class.java)
        val sale = mock(Sale::class.java)

        given(sale.id)
            .willReturn(saleId)

        given(sale.endAt)
            .willReturn(
                LocalDateTime.now()
                    .plusHours(1)
            )

        val lockedPurchase = Purchase.create(user, sale, 1, 15_000)

        val payment = Payment.create(lockedPurchase, 15_000)

        given(paymentService.getPaymentByPurchaseIdWithLock(purchaseId))
            .willReturn(payment)

        given(purchaseRepository.findByIdAndUserIdWithLock(purchaseId, userId))
            .willReturn(Optional.of(lockedPurchase))

        // when
        val response = purchaseService.cancel(userId, purchaseId)

        // then
        assertThat(payment.status)
            .isEqualTo(PaymentStatus.CANCELED)

        assertThat(payment.canceledAt)
            .isNotNull()

        assertThat(response.status)
            .isEqualTo(PurchaseStatus.CANCELED)

        assertThat(response.canceledAt)
            .isNotNull()

        verify(paymentService)
            .getPaymentByPurchaseIdWithLock(purchaseId)

        verify(outboxEventService)
            .saveStockRestoreRequested(
                isNull(),
                eq(saleId),
                eq(userId),
                eq(1)
            )

        verify(inventoryService, never())
            .restoreStock(
                anyLong(),
                anyLong(),
                anyInt()
            )
    }

    @Test
    @DisplayName("결제 완료된 구매를 취소하면 결제를 취소하고 재고를 복구한다")
    fun cancelCompletedPurchaseSuccess() {
        // given
        val user = mock(User::class.java)
        val sale = mock(Sale::class.java)
        val payment = mock(Payment::class.java)

        given(sale.id)
            .willReturn(saleId)

        given(sale.endAt)
            .willReturn(
                LocalDateTime.now()
                    .plusHours(1)
            )

        val lockedPurchase = Purchase.create(user, sale, 1, 15_000)

        lockedPurchase.complete()

        given(paymentService.getPaymentByPurchaseIdWithLock(purchaseId))
            .willReturn(payment)

        given(purchaseRepository.findByIdAndUserIdWithLock(purchaseId, userId))
            .willReturn(Optional.of(lockedPurchase))

        given(payment.paymentKey)
            .willReturn("payment-key")

        // when
        val response = purchaseService.cancel(userId, purchaseId)

        // then
        verify(paymentService)
            .getPaymentByPurchaseIdWithLock(purchaseId)

        verify(paymentService)
            .cancelCompletedPayment("payment-key")

        verify(outboxEventService)
            .saveStockRestoreRequested(
                isNull(),
                eq(saleId),
                eq(userId),
                eq(1)
            )

        verify(inventoryService, never())
            .restoreStock(
                anyLong(),
                anyLong(),
                anyInt()
            )

        assertThat(lockedPurchase.status)
            .isEqualTo(PurchaseStatus.CANCELED)

        assertThat(response.status)
            .isEqualTo(PurchaseStatus.CANCELED)

        assertThat(response.canceledAt)
            .isNotNull()
    }
    // Mockito returns a null placeholder; an unconstrained type parameter avoids
    // a Kotlin non-null check before the mock records the matcher.
    private fun <T> anyValue(type: Class<T>): T = any(type)
}
