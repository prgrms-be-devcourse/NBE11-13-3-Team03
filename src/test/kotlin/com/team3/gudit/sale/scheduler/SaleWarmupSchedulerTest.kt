package com.team3.gudit.sale.scheduler

import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.sale.domain.enums.SaleStatus
import com.team3.gudit.sale.domain.repository.SaleRepository
import com.team3.gudit.sale.service.InventoryService
import com.team3.gudit.sale.service.SaleService
import com.team3.gudit.testsupport.anyValue
import com.team3.gudit.testsupport.eqValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.InOrder
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class SaleWarmupSchedulerTest {
    @Mock
    private lateinit var saleRepository: SaleRepository

    @Mock
    private lateinit var inventoryService: InventoryService

    @Mock
    private lateinit var saleService: SaleService

    private lateinit var scheduler: SaleWarmupScheduler

    @BeforeEach
    fun setUp() {
        scheduler = SaleWarmupScheduler(saleRepository, inventoryService, saleService)
    }

    @Test
    @DisplayName("판매 시작 10분 이내의 READY 판매를 Warm-up한다")
    fun autoWarmupSales() {
        val sale = mock(Sale::class.java)
        given(sale.id).willReturn(1L)
        given(
            saleRepository.findByStatusAndStartAtBetween(
                eqValue(SaleStatus.READY),
                anyValue(LocalDateTime::class.java),
                anyValue(LocalDateTime::class.java),
            ),
        ).willReturn(listOf(sale))

        scheduler.autoWarmupSales()

        verify(saleService).warmupSaleInfo(1L)
        verify(saleService, never()).startSale(anyLong())
    }

    @Test
    @DisplayName("한 판매의 Warm-up 실패가 다음 판매의 Warm-up을 막지 않는다")
    fun autoWarmupContinuesAfterFailure() {
        val firstSale = mock(Sale::class.java)
        val secondSale = mock(Sale::class.java)
        given(firstSale.id).willReturn(1L)
        given(secondSale.id).willReturn(2L)
        given(
            saleRepository.findByStatusAndStartAtBetween(
                eqValue(SaleStatus.READY),
                anyValue(LocalDateTime::class.java),
                anyValue(LocalDateTime::class.java),
            ),
        ).willReturn(listOf(firstSale, secondSale))
        willThrow(RuntimeException("Redis 오류"))
            .given(saleService)
            .warmupSaleInfo(1L)

        scheduler.autoWarmupSales()

        verify(saleService).warmupSaleInfo(1L)
        verify(saleService).warmupSaleInfo(2L)
    }

    @Test
    @DisplayName("판매 시작 대상은 Warm-up 후 ON_SALE 전환을 수행한다")
    fun startSales() {
        val sale = mock(Sale::class.java)
        given(sale.id).willReturn(1L)
        given(
            saleRepository.findByStatusAndStartAtLessThanEqualAndEndAtGreaterThan(
                eqValue(SaleStatus.READY),
                anyValue(LocalDateTime::class.java),
                anyValue(LocalDateTime::class.java),
            ),
        ).willReturn(listOf(sale))

        scheduler.startSales()

        val ordered: InOrder = inOrder(saleService)
        ordered.verify(saleService).warmupSaleInfo(1L)
        ordered.verify(saleService).startSale(1L)
    }

    @Test
    @DisplayName("한 판매의 시작 실패가 다음 판매 시작을 막지 않는다")
    fun startSalesContinuesAfterFailure() {
        val firstSale = mock(Sale::class.java)
        val secondSale = mock(Sale::class.java)
        given(firstSale.id).willReturn(1L)
        given(secondSale.id).willReturn(2L)
        given(
            saleRepository.findByStatusAndStartAtLessThanEqualAndEndAtGreaterThan(
                eqValue(SaleStatus.READY),
                anyValue(LocalDateTime::class.java),
                anyValue(LocalDateTime::class.java),
            ),
        ).willReturn(listOf(firstSale, secondSale))
        willThrow(RuntimeException("판매 시작 실패"))
            .given(saleService)
            .startSale(1L)

        scheduler.startSales()

        verify(saleService).startSale(1L)
        verify(saleService).startSale(2L)
    }

    @Test
    @DisplayName("종료 시간이 지난 ON_SALE 판매를 종료 처리한다")
    fun endSales() {
        val sale = mock(Sale::class.java)
        given(sale.id).willReturn(1L)
        given(
            saleRepository.findByStatusAndEndAtLessThanEqual(
                eqValue(SaleStatus.ON_SALE),
                anyValue(LocalDateTime::class.java),
            ),
        ).willReturn(listOf(sale))

        scheduler.endSales()

        verify(saleService).endSale(1L)
    }
}
