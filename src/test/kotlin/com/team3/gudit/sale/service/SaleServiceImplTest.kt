package com.team3.gudit.sale.service

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.goods.domain.entity.Goods
import com.team3.gudit.goods.domain.enums.GoodsStatus
import com.team3.gudit.goods.domain.repository.GoodsRepository
import com.team3.gudit.purchase.entity.PurchaseStatus
import com.team3.gudit.purchase.repository.PurchaseRepository
import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.sale.domain.enums.SaleStatus
import com.team3.gudit.sale.domain.repository.SaleRepository
import com.team3.gudit.sale.dto.SaleRedisDto
import com.team3.gudit.sale.dto.reqeust.SaleCreateRequestDto
import com.team3.gudit.sale.dto.reqeust.SaleUpdateRequestDto
import com.team3.gudit.sale.exception.SaleErrorCode
import com.team3.gudit.testsupport.anyValue
import com.team3.gudit.testsupport.eqValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.InOrder
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class SaleServiceImplTest {
    @Mock
    private lateinit var saleRepository: SaleRepository

    @Mock
    private lateinit var goodsRepository: GoodsRepository

    @Mock
    private lateinit var redisTemplate: StringRedisTemplate

    @Mock
    private lateinit var purchaseRepository: PurchaseRepository

    @Mock
    private lateinit var valueOperations: ValueOperations<String, String>

    @Mock
    private lateinit var hashOperations: HashOperations<String, Any, Any>

    private lateinit var saleService: SaleServiceImpl

    @BeforeEach
    fun setUp() {
        saleService = SaleServiceImpl(saleRepository, goodsRepository, redisTemplate, purchaseRepository)
    }

    @Test
    @DisplayName("판매 생성 시 로그인한 관리자 ID를 createdBy에 저장한다")
    fun createSaleStoresCreatedBy() {
        val startAt = LocalDateTime.now().plusDays(1)
        val request = SaleCreateRequestDto(10L, 100, 2, startAt, startAt.plusHours(2))
        given(goodsRepository.findById(10L)).willReturn(Optional.of(goods()))
        given(saleRepository.save(anyValue(Sale::class.java))).willAnswer { it.getArgument(0) }

        saleService.createSale(request, 7L)

        val captor = ArgumentCaptor.forClass(Sale::class.java)
        verify(saleRepository).save(captor.capture())
        assertThat(captor.value.createdBy).isEqualTo(7L)
    }

    @Test
    @DisplayName("Warm-up은 판매 상태를 변경하지 않고 Redis stock과 info를 저장한다")
    fun warmupSaleInfo() {
        val sale = createSale(SaleStatus.READY, 100, LocalDateTime.now().plusDays(1))
        prepareWarmup(sale)

        saleService.warmupSaleInfo(1L)

        verify(valueOperations).set("sale:1:stock", "100")
        verify(hashOperations).putAll("sale:1:info", SaleRedisDto.from(sale).toHashFields())
        assertThat(sale.status).isEqualTo(SaleStatus.READY)
    }

    @Test
    @DisplayName("Warm-up 시 stock과 info 키에 동일한 endAt + 2일 기준 TTL을 적용한다")
    fun warmupSaleInfoAppliesTtl() {
        val sale = createSale(SaleStatus.READY, 100, LocalDateTime.now().plusDays(1))
        prepareWarmup(sale)

        saleService.warmupSaleInfo(1L)

        val stockTtlCaptor = ArgumentCaptor.forClass(Duration::class.java)
        val infoTtlCaptor = ArgumentCaptor.forClass(Duration::class.java)
        verify(redisTemplate).expire(eqValue("sale:1:stock"), stockTtlCaptor.capture())
        verify(redisTemplate).expire(eqValue("sale:1:info"), infoTtlCaptor.capture())
        assertThat(stockTtlCaptor.value.isPositive).isTrue()
        assertThat(infoTtlCaptor.value.isPositive).isTrue()
        assertThat(stockTtlCaptor.value).isEqualTo(infoTtlCaptor.value)
    }

    @Test
    @DisplayName("READY 판매라도 endAt + 2일이 지났으면 Redis 캐시를 즉시 삭제한다")
    fun warmupExpiredSaleDeletesCache() {
        val sale = createSale(SaleStatus.READY, 20, LocalDateTime.now().minusDays(3))
        prepareWarmup(sale)

        saleService.warmupSaleInfo(1L)

        verify(redisTemplate).delete(listOf("sale:1:stock", "sale:1:info"))
        verify(redisTemplate, never()).expire(anyValue(String::class.java), anyValue(Duration::class.java))
    }

    @ParameterizedTest
    @EnumSource(value = SaleStatus::class, names = ["READY"], mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("READY가 아닌 판매는 Warm-up할 수 없고 Redis 데이터를 변경하지 않는다")
    fun warmupNonReadySaleIsRejected(status: SaleStatus) {
        val sale = createSale(status, 100, LocalDateTime.now().plusDays(1))
        given(saleRepository.findById(1L)).willReturn(Optional.of(sale))

        assertThatThrownBy { saleService.warmupSaleInfo(1L) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ exception: Throwable ->
                assertThat((exception as BusinessException).errorCode)
                    .isEqualTo(SaleErrorCode.CANNOT_WARMUP_NON_READY_SALE)
            })
        verifyNoInteractions(redisTemplate)
    }

    @Test
    @DisplayName("판매 수정 시 전체 정보를 변경하고 Redis 캐시와 TTL을 다시 설정한다")
    fun updateSaleRewarmsCache() {
        val sale = createSale(SaleStatus.READY, 100, LocalDateTime.now().plusDays(1))
        val updatedStartAt = LocalDateTime.now().plusDays(2)
        val updatedEndAt = updatedStartAt.plusHours(2)
        val request = SaleUpdateRequestDto(200, 5, updatedStartAt, updatedEndAt)
        given(saleRepository.findById(1L)).willReturn(Optional.of(sale))
        given(redisTemplate.opsForValue()).willReturn(valueOperations)
        given(redisTemplate.opsForHash<Any, Any>()).willReturn(hashOperations)

        val response = saleService.updateSale(1L, request)

        assertThat(sale.initialStock).isEqualTo(200)
        assertThat(sale.remainingStock).isEqualTo(200)
        assertThat(sale.maxPurchaseQuantity).isEqualTo(5)
        assertThat(sale.startAt).isEqualTo(updatedStartAt)
        assertThat(sale.endAt).isEqualTo(updatedEndAt)
        assertThat(response.remainingStock).isEqualTo(200)
        verify(valueOperations).set("sale:1:stock", "200")
        verify(redisTemplate).expire(eqValue("sale:1:stock"), anyValue(Duration::class.java))
        verify(redisTemplate).expire(eqValue("sale:1:info"), anyValue(Duration::class.java))
        verify(saleRepository, times(2)).findById(1L)
    }

    @Test
    @DisplayName("판매 삭제 시 Sale을 DELETED로 변경하고 Redis stock과 info 키를 삭제한다")
    fun deleteSaleDeletesRedisKeys() {
        val sale = createSale(SaleStatus.READY, 100, LocalDateTime.now().plusDays(1))
        given(saleRepository.findById(1L)).willReturn(Optional.of(sale))

        saleService.deleteSale(1L)

        assertThat(sale.status).isEqualTo(SaleStatus.DELETED)
        verify(redisTemplate).delete(listOf("sale:1:stock", "sale:1:info"))
    }

    @Test
    @DisplayName("판매 시작 시 RDB와 Redis 상태를 ON_SALE로 변경한다")
    fun startSale() {
        val sale = createSale(SaleStatus.READY, 100, LocalDateTime.now().plusHours(1))
        given(saleRepository.findById(1L)).willReturn(Optional.of(sale))
        given(redisTemplate.opsForHash<Any, Any>()).willReturn(hashOperations)

        saleService.startSale(1L)

        assertThat(sale.status).isEqualTo(SaleStatus.ON_SALE)
        verify(hashOperations).put("sale:1:info", "status", SaleStatus.ON_SALE.name)
    }

    @Test
    @DisplayName("판매 종료 시 Redis 구매를 먼저 차단하고 최종 재고를 RDB에 동기화한다")
    fun endSale() {
        val sale = createSale(SaleStatus.ON_SALE, 100, LocalDateTime.now().plusHours(1))
        given(saleRepository.findById(1L)).willReturn(Optional.of(sale))
        given(redisTemplate.opsForHash<Any, Any>()).willReturn(hashOperations)
        given(redisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.get("sale:1:stock")).willReturn("35")

        saleService.endSale(1L)

        assertThat(sale.status).isEqualTo(SaleStatus.CLOSED)
        assertThat(sale.remainingStock).isEqualTo(35)
        val ordered: InOrder = inOrder(hashOperations, valueOperations)
        ordered.verify(hashOperations).put("sale:1:info", "status", SaleStatus.CLOSED.name)
        ordered.verify(valueOperations).get("sale:1:stock")
    }

    @Test
    @DisplayName("ON_SALE 판매 상세 조회 시 Redis 실시간 재고를 우선 사용한다")
    fun saleDetailUsesRedisStock() {
        val sale = prepareDetailSale(100, "35")

        val response = saleService.saleDetail(1L)

        assertThat(response.remainingStock).isEqualTo(35)
        assertThat(response.status).isEqualTo(SaleStatus.ON_SALE)
        assertThat(sale.remainingStock).isEqualTo(100)
        assertThat(sale.status).isEqualTo(SaleStatus.ON_SALE)
    }

    @Test
    @DisplayName("ON_SALE 판매의 Redis 재고가 0이면 응답에서만 SOLD_OUT으로 표시한다")
    fun saleDetailDisplaysSoldOut() {
        val sale = prepareDetailSale(100, "0")

        val response = saleService.saleDetail(1L)

        assertThat(response.remainingStock).isZero()
        assertThat(response.status).isEqualTo(SaleStatus.SOLD_OUT)
        assertThat(sale.status).isEqualTo(SaleStatus.ON_SALE)
    }

    @Test
    @DisplayName("Redis stock 키가 없거나 값이 숫자가 아니면 RDB 재고로 fallback한다")
    fun saleDetailFallsBackToRdbStock() {
        listOf<String?>(null, "invalid-stock").forEach { redisStock ->
            prepareDetailSale(70, redisStock)
            val response = saleService.saleDetail(1L)
            assertThat(response.remainingStock).isEqualTo(70)
            assertThat(response.status).isEqualTo(SaleStatus.ON_SALE)
        }
    }

    @Test
    @DisplayName("READY 판매 조회 시 Redis를 조회하지 않고 RDB 재고를 사용한다")
    fun saleDetailUsesRdbStockWhenReady() {
        val sale = createSale(SaleStatus.READY, 100, LocalDateTime.now().plusHours(1))
        given(saleRepository.findById(1L)).willReturn(Optional.of(sale))

        val response = saleService.saleDetail(1L)

        assertThat(response.remainingStock).isEqualTo(100)
        assertThat(response.status).isEqualTo(SaleStatus.READY)
        verify(redisTemplate, never()).opsForValue()
    }

    @Test
    @DisplayName("취소 유예기간이 지나고 미결제 구매가 없으면 Redis 최종 재고를 RDB에 동기화한다")
    fun syncFinalRemainingStock() {
        val sale = prepareFinalSyncSale(pendingPurchase = false, redisStock = "42")

        val synced = saleService.syncFinalRemainingStock(1L)

        assertThat(synced).isTrue()
        assertThat(sale.remainingStock).isEqualTo(42)
        assertThat(sale.finalStockSyncedAt).isNotNull()
        verify(saleRepository).findByIdWithLock(1L)
    }

    @Test
    @DisplayName("취소 유예기간이 지나지 않았으면 최종 재고 동기화를 보류한다")
    fun syncFinalRemainingStockBeforeDeadline() {
        val sale = createSale(SaleStatus.CLOSED, 100, LocalDateTime.now().minusHours(1))
        given(saleRepository.findByIdWithLock(1L)).willReturn(Optional.of(sale))

        val synced = saleService.syncFinalRemainingStock(1L)

        assertThat(synced).isFalse()
        assertThat(sale.finalStockSyncedAt).isNull()
        verifyNoInteractions(purchaseRepository)
        verifyNoInteractions(redisTemplate)
    }

    @Test
    @DisplayName("PENDING_PAYMENT 구매가 남아 있으면 최종 재고 동기화를 보류한다")
    fun syncFinalRemainingStockWithPendingPurchase() {
        val sale = createSale(SaleStatus.CLOSED, 100, LocalDateTime.now().minusDays(2))
        given(saleRepository.findByIdWithLock(1L)).willReturn(Optional.of(sale))
        given(purchaseRepository.existsBySaleIdAndStatus(1L, PurchaseStatus.PENDING_PAYMENT)).willReturn(true)

        val synced = saleService.syncFinalRemainingStock(1L)

        assertThat(synced).isFalse()
        assertThat(sale.finalStockSyncedAt).isNull()
        verifyNoInteractions(redisTemplate)
    }

    @Test
    @DisplayName("최종 동기화할 Redis stock 키가 없으면 RDB 재고를 변경하지 않는다")
    fun syncFinalRemainingStockWithoutRedisKey() {
        val sale = prepareFinalSyncSale(pendingPurchase = false, redisStock = null)

        val synced = saleService.syncFinalRemainingStock(1L)

        assertThat(synced).isFalse()
        assertThat(sale.remainingStock).isEqualTo(100)
        assertThat(sale.finalStockSyncedAt).isNull()
    }

    private fun prepareWarmup(sale: Sale) {
        given(saleRepository.findById(1L)).willReturn(Optional.of(sale))
        given(redisTemplate.opsForValue()).willReturn(valueOperations)
        given(redisTemplate.opsForHash<Any, Any>()).willReturn(hashOperations)
    }

    private fun prepareDetailSale(
        remainingStock: Int,
        redisStock: String?,
    ): Sale {
        val sale = createSale(SaleStatus.ON_SALE, remainingStock, LocalDateTime.now().plusHours(1))
        given(saleRepository.findById(1L)).willReturn(Optional.of(sale))
        given(redisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.get("sale:1:stock")).willReturn(redisStock)
        return sale
    }

    private fun prepareFinalSyncSale(
        pendingPurchase: Boolean,
        redisStock: String?,
    ): Sale {
        val sale = createSale(SaleStatus.CLOSED, 100, LocalDateTime.now().minusDays(2))
        given(saleRepository.findByIdWithLock(1L)).willReturn(Optional.of(sale))
        given(purchaseRepository.existsBySaleIdAndStatus(1L, PurchaseStatus.PENDING_PAYMENT))
            .willReturn(pendingPurchase)
        if (!pendingPurchase) {
            given(redisTemplate.opsForValue()).willReturn(valueOperations)
            given(valueOperations.get("sale:1:stock")).willReturn(redisStock)
        }
        return sale
    }

    private fun goods(): Goods =
        Goods.builder()
            .id(10L)
            .name("테스트 상품")
            .description("테스트 설명")
            .price(10_000)
            .imageUrl("test-image.jpg")
            .status(GoodsStatus.ACTIVE)
            .build()

    private fun createSale(
        status: SaleStatus,
        remainingStock: Int,
        endAt: LocalDateTime,
    ): Sale =
        Sale.builder()
            .id(1L)
            .goods(goods())
            .initialStock(100)
            .remainingStock(remainingStock)
            .maxPurchaseQuantity(2)
            .status(status)
            .startAt(endAt.minusHours(1))
            .endAt(endAt)
            .build()
}
