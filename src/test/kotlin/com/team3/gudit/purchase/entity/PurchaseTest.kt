package com.team3.gudit.purchase.entity

import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.user.domain.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class PurchaseTest {

    @Test
    @DisplayName("구매 정보를 생성하면 결제 대기 상태로 생성된다")
    fun createPurchase() {
        // given
        val user = mock(User::class.java)
        val sale = mock(Sale::class.java)

        // when
        val purchase = Purchase.create(user, sale, 1, 15_000)

        // then
        assertThat(purchase.user).isEqualTo(user)
        assertThat(purchase.sale).isEqualTo(sale)
        assertThat(purchase.quantity).isEqualTo(1)
        assertThat(purchase.purchasePrice).isEqualTo(15_000)
        assertThat(purchase.status)
            .isEqualTo(PurchaseStatus.PENDING_PAYMENT)
        assertThat(purchase.purchasedAt).isNull()
        assertThat(purchase.canceledAt).isNull()
    }

    @Test
    @DisplayName("결제를 완료하면 구매 상태와 구매 완료 시간이 변경된다")
    fun completePurchase() {
        // given
        val user = mock(User::class.java)
        val sale = mock(Sale::class.java)

        val purchase = Purchase.create(user, sale, 1, 15_000)

        // when
        purchase.complete()

        // then
        assertThat(purchase.status)
            .isEqualTo(PurchaseStatus.PURCHASED)
        assertThat(purchase.purchasedAt).isNotNull()
    }

    @Test
    @DisplayName("구매를 취소하면 상태와 취소 시간이 변경된다")
    fun cancelPurchase() {
        // given
        val user = mock(User::class.java)
        val sale = mock(Sale::class.java)

        val purchase = Purchase.create(user, sale, 1, 15_000)

        // when
        purchase.cancel()

        // then
        assertThat(purchase.status)
            .isEqualTo(PurchaseStatus.CANCELED)
        assertThat(purchase.canceledAt).isNotNull()
    }
}
