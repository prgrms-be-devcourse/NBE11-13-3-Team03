package com.team3.gudit.sale.scheduler

import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.sale.domain.enums.SaleStatus
import com.team3.gudit.sale.domain.repository.SaleRepository
import com.team3.gudit.sale.service.SaleService
import com.team3.gudit.testsupport.anyValue
import com.team3.gudit.testsupport.eqValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class SaleStockSyncSchedulerTest {
    @Mock
    private lateinit var saleRepository: SaleRepository

    @Mock
    private lateinit var saleService: SaleService

    private lateinit var scheduler: SaleStockSyncScheduler

    @BeforeEach
    fun setUp() {
        scheduler = SaleStockSyncScheduler(saleRepository, saleService)
    }

    @Test
    @DisplayName("종료 후 1일이 지나고 최종 동기화되지 않은 CLOSED 판매를 동기화한다")
    fun syncFinalRemainingStocks() {
        val sale = mock(Sale::class.java)
        given(sale.id).willReturn(1L)
        given(
            saleRepository.findByStatusAndFinalStockSyncedAtIsNullAndEndAtLessThanEqual(
                eqValue(SaleStatus.CLOSED),
                anyValue(LocalDateTime::class.java),
            ),
        ).willReturn(listOf(sale))
        given(saleService.syncFinalRemainingStock(1L)).willReturn(true)

        scheduler.syncFinalRemainingStocks()

        verify(saleRepository).findByStatusAndFinalStockSyncedAtIsNullAndEndAtLessThanEqual(
            eqValue(SaleStatus.CLOSED),
            anyValue(LocalDateTime::class.java),
        )
        verify(saleService).syncFinalRemainingStock(1L)
    }

    @Test
    @DisplayName("한 판매의 최종 동기화 실패가 다음 판매 처리를 막지 않는다")
    fun syncFinalRemainingStocksContinuesAfterFailure() {
        val firstSale = mock(Sale::class.java)
        val secondSale = mock(Sale::class.java)
        given(firstSale.id).willReturn(1L)
        given(secondSale.id).willReturn(2L)
        given(
            saleRepository.findByStatusAndFinalStockSyncedAtIsNullAndEndAtLessThanEqual(
                eqValue(SaleStatus.CLOSED),
                anyValue(LocalDateTime::class.java),
            ),
        ).willReturn(listOf(firstSale, secondSale))
        given(saleService.syncFinalRemainingStock(1L)).willThrow(RuntimeException("동기화 실패"))
        given(saleService.syncFinalRemainingStock(2L)).willReturn(true)

        scheduler.syncFinalRemainingStocks()

        verify(saleService).syncFinalRemainingStock(1L)
        verify(saleService).syncFinalRemainingStock(2L)
    }

    @Test
    @DisplayName("최종 재고 동기화 대상이 없으면 서비스를 호출하지 않는다")
    fun syncFinalRemainingStocksWithoutTarget() {
        given(
            saleRepository.findByStatusAndFinalStockSyncedAtIsNullAndEndAtLessThanEqual(
                eqValue(SaleStatus.CLOSED),
                anyValue(LocalDateTime::class.java),
            ),
        ).willReturn(emptyList())

        scheduler.syncFinalRemainingStocks()

        verifyNoInteractions(saleService)
    }
}
