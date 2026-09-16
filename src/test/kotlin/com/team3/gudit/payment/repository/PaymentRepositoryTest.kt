package com.team3.gudit.payment.repository

import com.team3.gudit.auth.oauth2.AuthProvider
import com.team3.gudit.goods.config.JpaConfig
import com.team3.gudit.goods.domain.entity.Goods
import com.team3.gudit.payment.entity.Payment
import com.team3.gudit.purchase.entity.Purchase
import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.sale.domain.enums.SaleStatus
import com.team3.gudit.user.domain.entity.Role
import com.team3.gudit.user.domain.entity.User
import jakarta.persistence.EntityManager
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import

@DataJpaTest
@Import(JpaConfig::class)
class PaymentRepositoryTest {
    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    @DisplayName("주문 ID로 결제 정보를 조회한다")
    fun findByOrderId() {
        val payment = savePayment()
        entityManager.flush()
        entityManager.clear()

        val result = paymentRepository.findByOrderId(payment.orderId).orElseThrow()
        assertThat(result.id).isEqualTo(payment.id)
        assertThat(result.orderId).isEqualTo(payment.orderId)
    }

    @Test
    @DisplayName("결제 키로 결제 정보를 조회한다")
    fun findByPaymentKey() {
        val payment = savePayment()
        payment.start("test-payment-key")
        entityManager.flush()
        entityManager.clear()

        val result = paymentRepository.findByPaymentKey("test-payment-key").orElseThrow()
        assertThat(result.id).isEqualTo(payment.id)
        assertThat(result.paymentKey).isEqualTo("test-payment-key")
    }

    @Test
    @DisplayName("구매 ID로 연결된 결제 정보를 조회한다")
    fun findByPurchaseId() {
        val payment = savePayment()
        val purchaseId = payment.purchase.id
        entityManager.flush()
        entityManager.clear()

        val result = paymentRepository.findByPurchaseId(purchaseId).orElseThrow()
        assertThat(result.id).isEqualTo(payment.id)
        assertThat(result.purchase.id).isEqualTo(purchaseId)
    }

    private fun savePayment(): Payment {
        val user = User(
            null,
            System.nanoTime(),
            "테스트 사용자",
            "payment-test@example.com",
            Role.USER,
            AuthProvider.KAKAO,
            null,
            null
        )
        entityManager.persist(user)

        val goods = Goods.of(
            "결제 테스트 상품",
            "Payment Repository 테스트 상품",
            10_000,
            null
        )
        entityManager.persist(goods)

        val sale = Sale.builder()
            .goods(goods)
            .createdBy(user.id)
            .initialStock(100)
            .maxPurchaseQuantity(2)
            .status(SaleStatus.READY)
            .startAt(LocalDateTime.now().plusHours(1))
            .endAt(LocalDateTime.now().plusHours(2))
            .build()
        entityManager.persist(sale)

        val purchase = Purchase.create(user, sale, 1, 10_000)
        entityManager.persist(purchase)

        val payment = Payment.create(purchase, 10_000)
        entityManager.persist(payment)
        return payment
    }
}
