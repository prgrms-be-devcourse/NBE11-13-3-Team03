package com.team3.gudit.purchase.service

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.outbox.service.OutboxEventService
import com.team3.gudit.payment.entity.Payment
import com.team3.gudit.payment.service.PaymentService
import com.team3.gudit.purchase.dto.PurchaseCancelResponse
import com.team3.gudit.purchase.dto.PurchaseCreateResponse
import com.team3.gudit.purchase.dto.PurchaseDetailResponse
import com.team3.gudit.purchase.dto.PurchaseListResponse
import com.team3.gudit.purchase.dto.PurchaseSummaryResponse
import com.team3.gudit.purchase.entity.Purchase
import com.team3.gudit.purchase.entity.PurchaseStatus
import com.team3.gudit.purchase.exception.PurchaseErrorCode
import com.team3.gudit.purchase.repository.PurchaseRepository
import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.sale.domain.repository.SaleRepository
import com.team3.gudit.sale.exception.SaleErrorCode
import com.team3.gudit.sale.metrics.InventoryMetrics
import com.team3.gudit.sale.service.InventoryService
import com.team3.gudit.user.domain.entity.User
import com.team3.gudit.user.domain.repository.UserRepository
import com.team3.gudit.user.exception.UserErrorCode
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
@Transactional(readOnly = true)
class PurchaseService(
    private val purchaseRepository: PurchaseRepository,
    private val userRepository: UserRepository,
    private val saleRepository: SaleRepository,
    private val inventoryService: InventoryService,
    private val paymentService: PaymentService,
    // Legacy Java @InjectMocks can omit metrics when no synchronization is active.
    private val inventoryMetrics: InventoryMetrics?,
    private val outboxEventService: OutboxEventService,
) {
    @Transactional
    fun purchase(userId: Long, saleId: Long): PurchaseCreateResponse {
        if (purchaseRepository.existsByUserIdAndSaleIdAndStatusNot(userId, saleId, PurchaseStatus.CANCELED)) {
            throw BusinessException(PurchaseErrorCode.DUPLICATE_PURCHASE)
        }
        val user = userRepository.findById(userId).orElseThrow {
            BusinessException(UserErrorCode.USER_NOT_FOUND, "User not found. userId=" + userId)
        }
        val sale = saleRepository.findById(saleId).orElseThrow {
            BusinessException(SaleErrorCode.SALE_NOT_FOUND, "Sale not found. saleId=" + saleId)
        }
        inventoryService.decreaseStock(saleId, userId, 1)
        registerStockRollback(saleId, userId, 1)

        val purchasePrice: Int = sale.goods.price
        val purchase = Purchase.create(user, sale, 1, purchasePrice)
        val savedPurchase = purchaseRepository.save(purchase)
        val payment = paymentService.createPayment(savedPurchase)
        return PurchaseCreateResponse(
            savedPurchase.id, savedPurchase.sale.id, savedPurchase.quantity,
            savedPurchase.purchasePrice, savedPurchase.status, savedPurchase.purchasedAt,
            savedPurchase.createdAt, payment.orderId,
        )
    }

    fun getMyPurchases(userId: Long): PurchaseListResponse {
        val purchases = purchaseRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
            .stream().map(this::toSummaryResponse).toList()
        return PurchaseListResponse(purchases)
    }

    fun getPurchase(userId: Long, purchaseId: Long): PurchaseDetailResponse {
        val purchase = purchaseRepository.findByIdAndUserId(purchaseId, userId).orElseThrow {
            BusinessException(PurchaseErrorCode.PURCHASE_NOT_FOUND, "Purchase not found. purchaseId=" + purchaseId)
        }
        return toDetailResponse(purchase)
    }

    @Transactional
    fun cancel(userId: Long, purchaseId: Long): PurchaseCancelResponse {
        val payment = paymentService.getPaymentByPurchaseIdWithLock(purchaseId)
        val purchase = purchaseRepository.findByIdAndUserIdWithLock(purchaseId, userId).orElseThrow {
            BusinessException(PurchaseErrorCode.PURCHASE_NOT_FOUND, "Purchase not found. purchaseId=" + purchaseId)
        }
        if (purchase.status == PurchaseStatus.CANCELED) {
            throw BusinessException(PurchaseErrorCode.PURCHASE_ALREADY_CANCELED)
        }
        validateCancellationPeriod(purchase)
        if (purchase.status == PurchaseStatus.PENDING_PAYMENT) {
            cancelPendingPayment(purchase, payment, userId)
        } else if (purchase.status == PurchaseStatus.PURCHASED) {
            cancelCompletedPayment(purchase, payment, userId)
        }
        return PurchaseCancelResponse(purchase.id, purchase.status, purchase.canceledAt)
    }

    private fun cancelPendingPayment(purchase: Purchase, payment: Payment, userId: Long) {
        payment.cancelReady()
        purchase.cancel()
        outboxEventService.saveStockRestoreRequested(purchase.id, purchase.sale.id, userId, purchase.quantity)
    }

    private fun cancelCompletedPayment(purchase: Purchase, payment: Payment, userId: Long) {
        paymentService.cancelCompletedPayment(payment.paymentKey)
        purchase.cancel()
        outboxEventService.saveStockRestoreRequested(purchase.id, purchase.sale.id, userId, purchase.quantity)
    }

    private fun toSummaryResponse(purchase: Purchase): PurchaseSummaryResponse = PurchaseSummaryResponse(
        purchase.id, purchase.sale.id, purchase.sale.goods.id,
        purchase.sale.goods.name, purchase.sale.goods.imageUrl, purchase.quantity,
        purchase.purchasePrice, purchase.status, purchase.purchasedAt,
    )

    private fun toDetailResponse(purchase: Purchase): PurchaseDetailResponse = PurchaseDetailResponse(
        purchase.id, purchase.sale.id, purchase.sale.goods.id,
        purchase.sale.goods.name, purchase.sale.goods.imageUrl, purchase.quantity,
        purchase.purchasePrice, purchase.status, purchase.purchasedAt, purchase.canceledAt,
    )

    private fun registerStockRollback(saleId: Long, userId: Long, quantity: Int) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return
                }
                try {
                    inventoryService.restoreStock(saleId, userId, quantity)
                    val metrics = inventoryMetrics ?: throw NullPointerException("inventoryMetrics")
                    metrics.recordRollback("success")
                } catch (exception: RuntimeException) {
                    val metrics = inventoryMetrics ?: throw NullPointerException("inventoryMetrics")
                    metrics.recordRollback("failed")
                    throw exception
                }
            }
        })
    }

    private fun validateCancellationPeriod(purchase: Purchase) {
        val cancellationDeadline = purchase.sale.endAt.plusDays(1)
        if (!LocalDateTime.now().isBefore(cancellationDeadline)) {
            throw BusinessException(PurchaseErrorCode.PURCHASE_CANCELLATION_PERIOD_EXPIRED)
        }
    }
}
