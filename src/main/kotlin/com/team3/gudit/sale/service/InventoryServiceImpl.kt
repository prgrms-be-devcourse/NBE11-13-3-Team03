package com.team3.gudit.sale.service

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.sale.domain.repository.SaleRepository
import com.team3.gudit.sale.exception.SaleErrorCode
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@ConditionalOnProperty(
    prefix = "gudit.inventory",
    name = ["mode"],
    havingValue = "db-pessimistic",
)
@Transactional(readOnly = true)
class InventoryServiceImpl(
    private val saleRepository: SaleRepository,
) : InventoryService {
    @Transactional
    override fun decreaseStock(
        saleId: Long?,
        userId: Long?,
        quantity: Int,
    ) {
        validateQuantity(quantity)

        val sale =
            saleRepository.findByIdWithLock(saleId)
                .orElseThrow { BusinessException(SaleErrorCode.SALE_NOT_FOUND) }

        sale.validateSalePeriod()
        sale.validatePurchaseQuantity(quantity)
        sale.decreaseStock(quantity)
    }

    @Transactional
    override fun restoreStock(
        saleId: Long?,
        userId: Long?,
        quantity: Int,
    ) {
        validateQuantity(quantity)

        val sale =
            saleRepository.findByIdWithLock(saleId)
                .orElseThrow { BusinessException(SaleErrorCode.SALE_NOT_FOUND) }

        sale.restoreStock(quantity)
    }

    override fun restoreStockIdempotently(
        eventId: String?,
        saleId: Long?,
        userId: Long?,
        quantity: Int,
    ) {
        restoreStock(saleId, userId, quantity)
    }

    private fun validateQuantity(quantity: Int) {
        if (quantity <= 0) {
            throw BusinessException(SaleErrorCode.INVALID_PURCHASE_QUANTITY)
        }
    }
}
