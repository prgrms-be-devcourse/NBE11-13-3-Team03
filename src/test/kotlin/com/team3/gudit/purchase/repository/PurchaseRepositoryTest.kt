package com.team3.gudit.purchase.repository

import com.team3.gudit.auth.oauth2.AuthProvider
import com.team3.gudit.goods.config.JpaConfig
import com.team3.gudit.goods.domain.entity.Goods
import com.team3.gudit.purchase.entity.Purchase
import com.team3.gudit.purchase.entity.PurchaseStatus
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
class PurchaseRepositoryTest {

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    @DisplayName("사용자와 판매에 대해 취소되지 않은 구매가 존재하는지 조회한다")
    fun existsByUserIdAndSaleIdAndStatusNot() {
        // Given
        val data = saveTestData()

        val purchase = Purchase.create(data.user, data.sale, 2, 20_000)
        purchaseRepository.save(purchase)

        entityManager.flush()
        entityManager.clear()

        // When
        val exists =
        purchaseRepository.existsByUserIdAndSaleIdAndStatusNot(data.user.id, data.sale.id, PurchaseStatus.CANCELED)

        // Then
        assertThat(exists).isTrue()
    }

    @Test
    @DisplayName("사용자 ID로 구매 목록을 최신 생성순으로 조회한다")
    fun findAllByUserIdOrderByCreatedAtDesc() {
        // Given
        val data = saveTestData()

        val firstPurchase = Purchase.create(data.user, data.sale, 1, 10_000)
        purchaseRepository.save(firstPurchase)

        entityManager.flush()

        val secondPurchase = Purchase.create(data.user, data.sale, 1, 20_000)
        purchaseRepository.save(secondPurchase)

        entityManager.flush()
        entityManager.clear()

        // When
        val result = purchaseRepository
            .findAllByUserIdOrderByCreatedAtDesc(data.user.id)

        // Then
        assertThat(result.map { it.id
        })
            .containsExactly(secondPurchase.id, firstPurchase.id)
    }

    @Test
    @DisplayName("구매 ID와 사용자 ID가 모두 일치하는 구매를 조회한다")
    fun findByIdAndUserId() {
        // Given
        val data = saveTestData()

        val purchase = Purchase.create(data.user, data.sale, 1, 10_000)
        purchaseRepository.save(purchase)

        entityManager.flush()
        entityManager.clear()

        // When
        val result = purchaseRepository.findByIdAndUserId(purchase.id, data.user.id)
            .orElseThrow()

        // Then
        assertThat(result.id).isEqualTo(purchase.id)
        assertThat(result.user.id)
            .isEqualTo(data.user.id)
    }

    @Test
    @DisplayName("기준 시각 이전에 생성된 PENDING_PAYMENT 구매를 조회한다")
    fun findAllByStatusAndCreatedAtBefore() {
        // Given
        val data = saveTestData()

        val pendingPurchase = Purchase.create(data.user, data.sale, 1, 10_000)
        purchaseRepository.save(pendingPurchase)

        val completedPurchase = Purchase.create(data.user, data.sale, 1, 10_000)
        completedPurchase.complete()
        purchaseRepository.save(completedPurchase)

        entityManager.flush()
        entityManager.clear()

        val threshold = LocalDateTime.now().plusMinutes(1)

        // When
        val result = purchaseRepository.findAllByStatusAndCreatedAtBefore(PurchaseStatus.PENDING_PAYMENT, threshold)

        // Then
        assertThat(result.map { it.id
        })
            .contains(pendingPurchase.id)
            .doesNotContain(completedPurchase.id)
    }

    @Test
    @DisplayName("구매 ID로 비관적 쓰기 잠금을 적용해 조회한다")
    fun findByIdWithLock() {
        // Given
        val data = saveTestData()

        val purchase = Purchase.create(data.user, data.sale, 1, 10_000)
        purchaseRepository.save(purchase)

        entityManager.flush()
        entityManager.clear()

        // When
        val result = purchaseRepository.findByIdWithLock(purchase.id)
            .orElseThrow()

        // Then
        assertThat(result.id).isEqualTo(purchase.id)
        assertThat(result.status)
            .isEqualTo(PurchaseStatus.PENDING_PAYMENT)
    }

    @Test
    @DisplayName("구매 ID와 사용자 ID로 비관적 쓰기 잠금을 적용해 조회한다")
    fun findByIdAndUserIdWithLock() {
        // Given
        val data = saveTestData()

        val purchase = Purchase.create(data.user, data.sale, 1, 10_000)
        purchaseRepository.save(purchase)

        entityManager.flush()
        entityManager.clear()

        // When
        val result = purchaseRepository.findByIdAndUserIdWithLock(purchase.id, data.user.id)
            .orElseThrow()

        // Then
        assertThat(result.id).isEqualTo(purchase.id)
        assertThat(result.user.id)
            .isEqualTo(data.user.id)
    }

    @Test
    @DisplayName("판매에 PENDING_PAYMENT 구매가 존재하는지 조회한다")
    fun existsBySaleIdAndStatus() {
        // Given
        val data = saveTestData()

        val purchase = Purchase.create(data.user, data.sale, 1, 10_000)
        purchaseRepository.save(purchase)

        entityManager.flush()
        entityManager.clear()

        // When
        val exists = purchaseRepository.existsBySaleIdAndStatus(data.sale.id, PurchaseStatus.PENDING_PAYMENT)

        // Then
        assertThat(exists).isTrue()
    }

    private fun saveTestData(): TestData {
        val user = User(
            null,
            System.nanoTime(),
            "테스트 사용자",
            "test@example.com",
            Role.USER,
            AuthProvider.KAKAO,
            null,
            null
        )
        entityManager.persist(user)

        val goods = Goods.of("테스트 상품", "Repository 테스트 상품", 10_000, null)
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

        return TestData(user, sale)
    }

    private data class TestData(val user: User, val sale: Sale)
}
