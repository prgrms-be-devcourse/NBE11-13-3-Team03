package com.team3.gudit.sale.domain.entity

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.global.exception.GlobalErrorCode
import com.team3.gudit.goods.domain.entity.Goods
import com.team3.gudit.sale.domain.enums.SaleStatus
import com.team3.gudit.sale.exception.SaleErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SaleTest {
    @Test
    @DisplayName("상품 없는 판매는 생성 단계에서 공통 입력 오류로 거절된다")
    fun rejectSaleWithoutGoods() {
        assertBusinessException(GlobalErrorCode.INVALID_INPUT_VALUE) {
            saleBuilder().goods(null).build()
        }
    }

    @Test
    @DisplayName("null 상태 변경은 거절하고 기존 판매 상태를 유지한다")
    fun rejectNullStatusWithoutChangingSale() {
        val sale = createSale()

        assertBusinessException(SaleErrorCode.INVALID_STATUS_TRANSITION) {
            sale.updateSaleStatus(null)
        }

        assertThat(sale.status).isEqualTo(SaleStatus.READY)
        assertThat(sale.remainingStock).isEqualTo(100)
    }

    @Test
    @DisplayName("판매 생성 시 남은 재고는 초기 재고로 설정되고 상태는 READY로 초기화된다")
    fun createSaleInitializesFields() {
        val goods = goods()
        val startAt = LocalDateTime.now().plusHours(1)
        val endAt = startAt.plusHours(1)

        val sale =
            Sale.builder()
                .goods(goods)
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(startAt)
                .endAt(endAt)
                .build()

        assertThat(sale.goods).isEqualTo(goods)
        assertThat(sale.initialStock).isEqualTo(100)
        assertThat(sale.remainingStock).isEqualTo(100)
        assertThat(sale.maxPurchaseQuantity).isEqualTo(2)
        assertThat(sale.startAt).isEqualTo(startAt)
        assertThat(sale.endAt).isEqualTo(endAt)
        assertThat(sale.status).isEqualTo(SaleStatus.READY)
    }

    @Test
    @DisplayName("초기 재고가 null, 0 또는 음수이면 판매를 생성할 수 없다")
    fun rejectInvalidInitialStock() {
        listOf<Int?>(null, 0, -1).forEach { stock ->
            assertBusinessException(SaleErrorCode.INVALID_INITIAL_STOCK) {
                saleBuilder().initialStock(stock).build()
            }
        }
    }

    @Test
    @DisplayName("최대 구매 수량이 null, 0 또는 음수이면 판매를 생성할 수 없다")
    fun rejectInvalidMaxPurchaseQuantity() {
        listOf<Int?>(null, 0, -1).forEach { quantity ->
            assertBusinessException(SaleErrorCode.INVALID_MAX_PURCHASE_QUANTITY) {
                saleBuilder().maxPurchaseQuantity(quantity).build()
            }
        }
    }

    @Test
    @DisplayName("판매 시작 또는 종료 시간이 null이면 판매를 생성할 수 없다")
    fun rejectMissingSalePeriod() {
        assertBusinessException(SaleErrorCode.INVALID_SALE_PERIOD) {
            saleBuilder().startAt(null).build()
        }
        assertBusinessException(SaleErrorCode.INVALID_SALE_PERIOD) {
            saleBuilder().endAt(null).build()
        }
    }

    @Test
    @DisplayName("판매 시작 시간이 종료 시간과 같거나 늦으면 판매를 생성할 수 없다")
    fun rejectInvalidSalePeriod() {
        val now = LocalDateTime.now().plusHours(1)
        assertBusinessException(SaleErrorCode.INVALID_SALE_PERIOD) {
            saleBuilder().startAt(now).endAt(now).build()
        }
        assertBusinessException(SaleErrorCode.INVALID_SALE_PERIOD) {
            saleBuilder().startAt(now.plusMinutes(1)).endAt(now).build()
        }
    }

    @Test
    @DisplayName("READY 상태에서는 판매 정보를 전체 수정할 수 있다")
    fun updateSaleInfoWhenReady() {
        val sale = createSale()
        val updatedStartAt = LocalDateTime.now().plusDays(1)
        val updatedEndAt = updatedStartAt.plusHours(2)

        sale.updateSaleInfo(200, 4, updatedStartAt, updatedEndAt)

        assertThat(sale.initialStock).isEqualTo(200)
        assertThat(sale.remainingStock).isEqualTo(200)
        assertThat(sale.maxPurchaseQuantity).isEqualTo(4)
        assertThat(sale.startAt).isEqualTo(updatedStartAt)
        assertThat(sale.endAt).isEqualTo(updatedEndAt)
    }

    @Test
    @DisplayName("판매 수정 입력이 잘못되면 기존 정보를 변경하지 않는다")
    fun updateSaleInfoWithInvalidInput() {
        val startAt = LocalDateTime.now().plusHours(1)
        val endAt = startAt.plusHours(1)
        val sale = saleBuilder(startAt, endAt).build()

        assertBusinessException(SaleErrorCode.INVALID_INITIAL_STOCK) {
            sale.updateSaleInfo(null, 4, startAt.plusDays(1), endAt.plusDays(1))
        }

        assertThat(sale.initialStock).isEqualTo(100)
        assertThat(sale.remainingStock).isEqualTo(100)
        assertThat(sale.maxPurchaseQuantity).isEqualTo(2)
        assertThat(sale.startAt).isEqualTo(startAt)
        assertThat(sale.endAt).isEqualTo(endAt)
    }

    @Test
    @DisplayName("ON_SALE 또는 CLOSED 상태에서는 판매 정보를 수정할 수 없다")
    fun rejectUpdateAfterSaleStarted() {
        listOf(SaleStatus.ON_SALE, SaleStatus.CLOSED).forEach { status ->
            val sale = createSale(status)
            assertBusinessException(SaleErrorCode.CANNOT_UPDATE_ONGOING_SALE) {
                sale.updateSaleInfo(
                    200,
                    4,
                    LocalDateTime.now().plusDays(1),
                    LocalDateTime.now().plusDays(2),
                )
            }
            assertThat(sale.status).isEqualTo(status)
            assertThat(sale.initialStock).isEqualTo(100)
            assertThat(sale.remainingStock).isEqualTo(100)
        }
    }

    @Test
    @DisplayName("READY에서 ON_SALE, ON_SALE에서 CLOSED로 변경할 수 있다")
    fun updateSaleStatusAlongValidPath() {
        val sale = createSale()
        sale.updateSaleStatus(SaleStatus.ON_SALE)
        assertThat(sale.status).isEqualTo(SaleStatus.ON_SALE)
        sale.updateSaleStatus(SaleStatus.CLOSED)
        assertThat(sale.status).isEqualTo(SaleStatus.CLOSED)
    }

    @Test
    @DisplayName("ON_SALE 상태를 READY, SOLD_OUT 또는 DELETED로 직접 변경할 수 없다")
    fun rejectInvalidTransitionFromOnSale() {
        listOf(SaleStatus.READY, SaleStatus.SOLD_OUT, SaleStatus.DELETED).forEach { target ->
            val sale = createSale(SaleStatus.ON_SALE)
            assertBusinessException(SaleErrorCode.INVALID_STATUS_TRANSITION) {
                sale.updateSaleStatus(target)
            }
            assertThat(sale.status).isEqualTo(SaleStatus.ON_SALE)
        }
    }

    @Test
    @DisplayName("CLOSED 상태를 다시 ON_SALE 상태로 변경할 수 없다")
    fun rejectTransitionFromClosed() {
        val sale = createSale(SaleStatus.CLOSED)
        assertBusinessException(SaleErrorCode.INVALID_STATUS_TRANSITION) {
            sale.updateSaleStatus(SaleStatus.ON_SALE)
        }
        assertThat(sale.status).isEqualTo(SaleStatus.CLOSED)
    }

    @Test
    @DisplayName("CLOSED 상태의 판매를 삭제하면 DELETED 상태가 된다")
    fun deleteSaleWhenClosed() {
        val sale = createSale(SaleStatus.CLOSED)
        sale.deleteSale()
        assertThat(sale.status).isEqualTo(SaleStatus.DELETED)
    }

    @Test
    @DisplayName("ON_SALE 상태의 판매는 삭제할 수 없다")
    fun rejectDeleteWhenOnSale() {
        val sale = createSale(SaleStatus.ON_SALE)
        assertBusinessException(SaleErrorCode.CANNOT_DELETE_ONGOING_SALE) {
            sale.deleteSale()
        }
        assertThat(sale.status).isEqualTo(SaleStatus.ON_SALE)
    }

    @Test
    @DisplayName("DELETED 상태의 판매를 다시 ON_SALE 상태로 변경할 수 없다")
    fun rejectTransitionFromDeleted() {
        val sale = createSale(SaleStatus.CLOSED)
        sale.deleteSale()
        assertBusinessException(SaleErrorCode.INVALID_STATUS_TRANSITION) {
            sale.updateSaleStatus(SaleStatus.ON_SALE)
        }
        assertThat(sale.status).isEqualTo(SaleStatus.DELETED)
    }

    @Test
    @DisplayName("현재와 동일한 판매 상태를 요청하면 기존 상태를 유지한다")
    fun updateSaleStatusToSameStatus() {
        val sale = createSale()
        assertThatCode { sale.updateSaleStatus(SaleStatus.READY) }.doesNotThrowAnyException()
        assertThat(sale.status).isEqualTo(SaleStatus.READY)
    }

    @Test
    @DisplayName("판매 기간 안이고 상태가 ON_SALE이면 구매 가능 검증을 통과한다")
    fun validateSalePeriodWhenOnSale() {
        val sale = activeSale(SaleStatus.ON_SALE)
        assertThatCode { sale.validateSalePeriod() }.doesNotThrowAnyException()
    }

    @Test
    @DisplayName("판매 시작 전이나 종료 후이면 구매할 수 없다")
    fun rejectPurchaseOutsideSalePeriod() {
        val futureStart = LocalDateTime.now().plusHours(1)
        val futureSale = createSale(SaleStatus.ON_SALE, futureStart, futureStart.plusHours(1))
        assertBusinessException(SaleErrorCode.INVALID_SALE_PERIOD) { futureSale.validateSalePeriod() }

        val pastEnd = LocalDateTime.now().minusHours(1)
        val endedSale = createSale(SaleStatus.ON_SALE, pastEnd.minusHours(1), pastEnd)
        assertBusinessException(SaleErrorCode.INVALID_SALE_PERIOD) { endedSale.validateSalePeriod() }
    }

    @Test
    @DisplayName("판매 기간 안이어도 READY 또는 CLOSED 상태이면 구매할 수 없다")
    fun rejectPurchaseWhenNotOnSale() {
        listOf(SaleStatus.READY, SaleStatus.CLOSED).forEach { status ->
            val sale = activeSale(status)
            assertBusinessException(SaleErrorCode.SALE_CLOSED) { sale.validateSalePeriod() }
        }
    }

    @Test
    @DisplayName("구매 수량이 최대 구매 가능 수량과 같으면 구매할 수 있다")
    fun validatePurchaseQuantityAtMaximum() {
        val sale = createSale()
        assertThatCode { sale.validatePurchaseQuantity(2) }.doesNotThrowAnyException()
    }

    @Test
    @DisplayName("구매 수량이 최대 구매 가능 수량을 초과하면 구매할 수 없다")
    fun rejectPurchaseQuantityOverMaximum() {
        val sale = createSale()
        assertBusinessException(SaleErrorCode.EXCEEDED_PURCHASE_QUANTITY) {
            sale.validatePurchaseQuantity(3)
        }
    }

    @Test
    @DisplayName("재고를 차감하면 남은 재고가 감소한다")
    fun decreaseStock() {
        val sale = createSale()
        sale.decreaseStock(10)
        assertThat(sale.remainingStock).isEqualTo(90)
    }

    @Test
    @DisplayName("남은 재고보다 많이 차감하면 재고가 유지되고 예외가 발생한다")
    fun rejectDecreaseOverRemainingStock() {
        val sale = createSale()
        assertBusinessException(SaleErrorCode.NOT_ENOUGH_STOCK) { sale.decreaseStock(101) }
        assertThat(sale.remainingStock).isEqualTo(100)
    }

    @Test
    @DisplayName("재고를 복구하면 남은 재고가 복구 수량만큼 증가한다")
    fun restoreStock() {
        val sale = createSale()
        sale.decreaseStock(10)
        sale.restoreStock(10)
        assertThat(sale.remainingStock).isEqualTo(100)
    }

    @Test
    @DisplayName("남은 재고를 동기화하면 입력한 값으로 변경된다")
    fun syncRemainingStock() {
        val sale = createSale()
        sale.syncRemainingStock(37)
        assertThat(sale.remainingStock).isEqualTo(37)
        assertThat(sale.initialStock).isEqualTo(100)
    }

    @Test
    @DisplayName("음수 재고는 남은 재고로 동기화할 수 없다")
    fun rejectNegativeRemainingStock() {
        val sale = createSale()
        assertBusinessException(SaleErrorCode.INVALID_REMAINING_STOCK) {
            sale.syncRemainingStock(-1)
        }
        assertThat(sale.remainingStock).isEqualTo(100)
    }

    private fun goods(): Goods = Goods.of("상품", "설명", 1000, null)

    private fun saleBuilder(
        startAt: LocalDateTime = LocalDateTime.now().plusHours(1),
        endAt: LocalDateTime = startAt.plusHours(1),
    ): Sale.SaleBuilder =
        Sale.builder()
            .goods(goods())
            .initialStock(100)
            .maxPurchaseQuantity(2)
            .startAt(startAt)
            .endAt(endAt)

    private fun createSale(
        status: SaleStatus = SaleStatus.READY,
        startAt: LocalDateTime = LocalDateTime.now().plusHours(1),
        endAt: LocalDateTime = startAt.plusHours(1),
    ): Sale = saleBuilder(startAt, endAt).status(status).build()

    private fun activeSale(status: SaleStatus): Sale {
        val startAt = LocalDateTime.now().minusMinutes(10)
        return createSale(status, startAt, startAt.plusHours(1))
    }

    private fun assertBusinessException(
        expected: Any,
        action: () -> Unit,
    ) {
        assertThatThrownBy(action)
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ exception: Throwable ->
                assertThat((exception as BusinessException).errorCode).isEqualTo(expected)
            })
    }
}
