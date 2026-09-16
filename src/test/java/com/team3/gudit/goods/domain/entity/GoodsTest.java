package com.team3.gudit.goods.domain.entity;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.global.exception.GlobalErrorCode;
import com.team3.gudit.goods.domain.enums.GoodsStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoodsTest {
    @Test
    void missingPriceRejectsUpdateWithoutPartialChanges() {
        Goods goods = Goods.of("기존 상품", "기존 설명", 1000, null);

        assertThatThrownBy(() -> goods.updateGoodsInfo("변경 상품", "변경 설명", null, "image"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE));

        assertThat(goods.getName()).isEqualTo("기존 상품");
        assertThat(goods.getDescription()).isEqualTo("기존 설명");
        assertThat(goods.getPrice()).isEqualTo(1000);
        assertThat(goods.getImageUrl()).isNull();
    }

    @Test
    void missingStatusRejectsUpdateWithoutChangingStatus() {
        Goods goods = Goods.of("상품", null, 1000, null);

        assertThatThrownBy(() -> goods.updateGoodsStatus(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE));

        assertThat(goods.getStatus()).isEqualTo(GoodsStatus.ACTIVE);
    }
}
