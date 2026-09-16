package com.team3.gudit.sale.service

import com.team3.gudit.sale.dto.reqeust.SaleCreateRequestDto
import com.team3.gudit.sale.dto.reqeust.SaleStatusUpdateRequestDto
import com.team3.gudit.sale.dto.reqeust.SaleUpdateRequestDto
import com.team3.gudit.sale.dto.response.SaleCreateResponseDto
import com.team3.gudit.sale.dto.response.SaleDetailResponseDto
import com.team3.gudit.sale.dto.response.SaleListResponseDto
import com.team3.gudit.sale.dto.response.SaleStatusUpdateResponseDto

interface SaleService {
    fun createSale(
        request: SaleCreateRequestDto,
        createdBy: Long,
    ): SaleCreateResponseDto

    fun saleDetail(id: Long?): SaleDetailResponseDto

    fun saleList(): List<SaleListResponseDto>

    fun updateSale(
        saleId: Long?,
        request: SaleUpdateRequestDto,
    ): SaleDetailResponseDto

    fun updateSaleStatus(
        saleId: Long?,
        request: SaleStatusUpdateRequestDto,
    ): SaleStatusUpdateResponseDto

    fun deleteSale(id: Long?)

    fun warmupSaleInfo(id: Long?)

    fun startSale(id: Long?)

    fun endSale(id: Long?)

    fun syncFinalRemainingStock(saleId: Long?): Boolean
}
