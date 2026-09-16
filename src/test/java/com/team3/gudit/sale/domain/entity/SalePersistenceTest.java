package com.team3.gudit.sale.domain.entity;

import com.team3.gudit.goods.domain.entity.Goods;
import com.team3.gudit.sale.dto.response.SaleCreateResponseDto;
import com.team3.gudit.sale.dto.response.SaleDetailResponseDto;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SalePersistenceTest {
    @Autowired
    private EntityManager entityManager;

    @Test
    void reloadSaleWithLazyGoodsAndRestoreStock() {
        Goods goods = Goods.of("재고 검증 상품", null, 1000, null);
        entityManager.persist(goods);
        LocalDateTime startAt = LocalDateTime.now().minusHours(1);
        Sale sale = Sale.builder()
                .goods(goods)
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(startAt)
                .endAt(startAt.plusHours(2))
                .build();
        entityManager.persist(sale);
        entityManager.flush();
        Long saleId = sale.getId();
        Long goodsId = goods.getId();
        entityManager.clear();

        Sale loaded = entityManager.find(Sale.class, saleId);
        assertThat(Hibernate.isInitialized(loaded.getGoods())).isFalse();
        assertThat(SaleCreateResponseDto.from(loaded).goodsId()).isEqualTo(goodsId);
        assertThat(SaleDetailResponseDto.from(loaded).goodsName()).isEqualTo("재고 검증 상품");
        loaded.decreaseStock(2);
        entityManager.flush();
        entityManager.clear();

        Sale decreased = entityManager.find(Sale.class, saleId);
        assertThat(decreased.getRemainingStock()).isEqualTo(98);
        decreased.restoreStock(2);
        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.find(Sale.class, saleId).getRemainingStock()).isEqualTo(100);
    }
}
