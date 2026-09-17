package com.team3.gudit.sale.domain.entity

import com.team3.gudit.goods.domain.entity.Goods
import com.team3.gudit.sale.dto.response.SaleCreateResponseDto
import com.team3.gudit.sale.dto.response.SaleDetailResponseDto
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.Hibernate
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@SpringBootTest
@Transactional
class SalePersistenceTest {
    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun reloadSaleWithLazyGoodsAndRestoreStock() {
        val goods = Goods.of("재고 검증 상품", null, 1000, null)
        entityManager.persist(goods)
        val startAt = LocalDateTime.now().minusHours(1)
        val sale =
            Sale.builder()
                .goods(goods)
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(startAt)
                .endAt(startAt.plusHours(2))
                .build()
        entityManager.persist(sale)
        entityManager.flush()
        val saleId = sale.id
        val goodsId = goods.id
        entityManager.clear()

        val loaded = entityManager.find(Sale::class.java, saleId)
        assertThat(Hibernate.isInitialized(loaded.goods)).isFalse()
        assertThat(SaleCreateResponseDto.from(loaded).goodsId).isEqualTo(goodsId)
        assertThat(SaleDetailResponseDto.from(loaded).goodsName).isEqualTo("재고 검증 상품")
        loaded.decreaseStock(2)
        entityManager.flush()
        entityManager.clear()

        val decreased = entityManager.find(Sale::class.java, saleId)
        assertThat(decreased.remainingStock).isEqualTo(98)
        decreased.restoreStock(2)
        entityManager.flush()
        entityManager.clear()
        assertThat(entityManager.find(Sale::class.java, saleId).remainingStock).isEqualTo(100)
    }
}
