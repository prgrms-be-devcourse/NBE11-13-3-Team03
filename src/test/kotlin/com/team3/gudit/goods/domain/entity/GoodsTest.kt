package com.team3.gudit.goods.domain.entity

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.global.exception.GlobalErrorCode
import com.team3.gudit.goods.domain.enums.GoodsStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GoodsTest {
    @Test
    fun missingPriceRejectsUpdateWithoutPartialChanges() {
        val goods = Goods.of("기존 상품", "기존 설명", 1000, null)

        assertThatThrownBy { goods.updateGoodsInfo("변경 상품", "변경 설명", null, "image") }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ exception: Throwable ->
                assertThat((exception as BusinessException).errorCode)
                    .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE)
            })

        assertThat(goods.name).isEqualTo("기존 상품")
        assertThat(goods.description).isEqualTo("기존 설명")
        assertThat(goods.price).isEqualTo(1000)
        assertThat(goods.imageUrl).isNull()
    }

    @Test
    fun missingStatusRejectsUpdateWithoutChangingStatus() {
        val goods = Goods.of("상품", null, 1000, null)

        assertThatThrownBy { goods.updateGoodsStatus(null) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ exception: Throwable ->
                assertThat((exception as BusinessException).errorCode)
                    .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE)
            })

        assertThat(goods.status).isEqualTo(GoodsStatus.ACTIVE)
    }
}
