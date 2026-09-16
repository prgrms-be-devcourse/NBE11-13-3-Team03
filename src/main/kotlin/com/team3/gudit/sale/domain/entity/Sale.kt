package com.team3.gudit.sale.domain.entity

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.global.exception.GlobalErrorCode
import com.team3.gudit.goods.domain.entity.Goods
import com.team3.gudit.sale.domain.enums.SaleStatus
import com.team3.gudit.sale.exception.SaleErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(name = "GOODS_SALES")
@EntityListeners(AuditingEntityListener::class)
class Sale protected constructor() {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "goods_id", nullable = false)
    lateinit var goods: Goods
        protected set

    @field:Column(name = "created_by")
    var createdBy: Long? = null
        protected set

    @field:Column(name = "initial_stock", nullable = false)
    var initialStock: Int = 0
        protected set

    @field:Column(name = "remaining_stock", nullable = false)
    var remainingStock: Int = 0
        protected set

    @field:Column(name = "max_purchase_quantity")
    var maxPurchaseQuantity: Int = 0
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false)
    var status: SaleStatus = SaleStatus.READY
        protected set

    @field:Column(name = "start_at", nullable = false)
    lateinit var startAt: LocalDateTime
        protected set

    @field:Column(name = "end_at", nullable = false)
    lateinit var endAt: LocalDateTime
        protected set

    @field:CreatedDate
    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
        protected set

    @field:LastModifiedDate
    @field:Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
        protected set

    @field:Column(name = "final_stock_synced_at")
    var finalStockSyncedAt: LocalDateTime? = null
        protected set

    protected constructor(
        id: Long?,
        goods: Goods?,
        createdBy: Long?,
        initialStock: Int?,
        remainingStock: Int?,
        maxPurchaseQuantity: Int?,
        status: SaleStatus?,
        startAt: LocalDateTime?,
        endAt: LocalDateTime?,
        createdAt: LocalDateTime?,
        updatedAt: LocalDateTime?,
        finalStockSyncedAt: LocalDateTime?,
    ) : this() {
        this.id = id
        this.goods = goods ?: throw BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE)
        this.createdBy = createdBy
        this.initialStock = initialStock ?: throw BusinessException(SaleErrorCode.INVALID_INITIAL_STOCK)
        this.remainingStock = remainingStock ?: throw BusinessException(SaleErrorCode.INVALID_REMAINING_STOCK)
        this.maxPurchaseQuantity = maxPurchaseQuantity ?: throw BusinessException(SaleErrorCode.INVALID_MAX_PURCHASE_QUANTITY)
        this.status = status ?: throw BusinessException(SaleErrorCode.INVALID_STATUS_TRANSITION)
        this.startAt = startAt ?: throw BusinessException(SaleErrorCode.INVALID_SALE_PERIOD)
        this.endAt = endAt ?: throw BusinessException(SaleErrorCode.INVALID_SALE_PERIOD)
        this.createdAt = createdAt
        this.updatedAt = updatedAt
        this.finalStockSyncedAt = finalStockSyncedAt
    }

    private constructor(
        id: Long?,
        goods: Goods?,
        createdBy: Long?,
        initialStock: Int?,
        remainingStock: Int?,
        maxPurchaseQuantity: Int?,
        status: SaleStatus?,
        startAt: LocalDateTime?,
        endAt: LocalDateTime?,
    ) : this() {
        validateSaleInput(
            initialStock,
            maxPurchaseQuantity,
            startAt,
            endAt,
        )

        this.id = id
        this.goods = goods ?: throw BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE)
        this.createdBy = createdBy
        this.initialStock = initialStock ?: throw BusinessException(SaleErrorCode.INVALID_INITIAL_STOCK)
        this.remainingStock = remainingStock ?: this.initialStock
        this.maxPurchaseQuantity = maxPurchaseQuantity ?: throw BusinessException(SaleErrorCode.INVALID_MAX_PURCHASE_QUANTITY)
        this.status = status ?: SaleStatus.READY
        this.startAt = startAt ?: throw BusinessException(SaleErrorCode.INVALID_SALE_PERIOD)
        this.endAt = endAt ?: throw BusinessException(SaleErrorCode.INVALID_SALE_PERIOD)
    }

    fun decreaseStock(count: Int) {
        val currentStock = remainingStock
        if (currentStock - count < 0) {
            throw BusinessException(SaleErrorCode.NOT_ENOUGH_STOCK)
        }
        remainingStock = currentStock - count
    }

    fun restoreStock(count: Int) {
        val currentStock = remainingStock
        remainingStock = currentStock + count
    }

    fun syncRemainingStock(remainingStock: Int) {
        if (remainingStock < 0) {
            throw BusinessException(SaleErrorCode.INVALID_REMAINING_STOCK)
        }
        this.remainingStock = remainingStock
    }

    fun completeFinalStockSync() {
        finalStockSyncedAt = LocalDateTime.now()
    }

    fun validateSalePeriod() {
        if (!isWithinSalePeriod()) {
            throw BusinessException(SaleErrorCode.INVALID_SALE_PERIOD)
        }
        if (status == SaleStatus.CLOSED) {
            throw BusinessException(SaleErrorCode.SALE_CLOSED)
        }
        if (status != SaleStatus.ON_SALE) {
            throw BusinessException(SaleErrorCode.SALE_CLOSED)
        }
    }

    fun validatePurchaseQuantity(purchaseQuantity: Int) {
        val maximum = maxPurchaseQuantity
        if (maximum < purchaseQuantity) {
            throw BusinessException(SaleErrorCode.EXCEEDED_PURCHASE_QUANTITY)
        }
    }

    fun updateSaleInfo(
        initialStock: Int?,
        maxPurchaseQuantity: Int?,
        startAt: LocalDateTime?,
        endAt: LocalDateTime?,
    ) {
        validateModifiable()
        validateSaleInput(
            initialStock,
            maxPurchaseQuantity,
            startAt,
            endAt,
        )

        this.initialStock = initialStock ?: throw BusinessException(SaleErrorCode.INVALID_INITIAL_STOCK)
        remainingStock = this.initialStock
        this.maxPurchaseQuantity = maxPurchaseQuantity ?: throw BusinessException(SaleErrorCode.INVALID_MAX_PURCHASE_QUANTITY)
        this.startAt = startAt ?: throw BusinessException(SaleErrorCode.INVALID_SALE_PERIOD)
        this.endAt = endAt ?: throw BusinessException(SaleErrorCode.INVALID_SALE_PERIOD)
    }

    fun updateSaleStatus(status: SaleStatus?) {
        if (status == null) {
            throw BusinessException(SaleErrorCode.INVALID_STATUS_TRANSITION)
        }
        if (this.status == status) {
            return
        }
        validateStatusTransition(status)
        this.status = status
    }

    fun validateDeletable() {
        if (status == SaleStatus.ON_SALE) {
            throw BusinessException(SaleErrorCode.CANNOT_DELETE_ONGOING_SALE)
        }
    }

    fun deleteSale() {
        validateDeletable()
        status = SaleStatus.DELETED
    }

    private fun isWithinSalePeriod(): Boolean {
        val currentStartAt = startAt
        val currentEndAt = endAt
        val now = LocalDateTime.now()
        return !now.isBefore(currentStartAt) && now.isBefore(currentEndAt)
    }

    private fun validateModifiable() {
        if (status != SaleStatus.READY) {
            throw BusinessException(SaleErrorCode.CANNOT_UPDATE_ONGOING_SALE)
        }
    }

    private fun validateStatusTransition(status: SaleStatus?) {
        if (status == SaleStatus.SOLD_OUT) {
            throw BusinessException(SaleErrorCode.INVALID_STATUS_TRANSITION)
        }
        if (this.status == SaleStatus.DELETED || this.status == SaleStatus.CLOSED) {
            throw BusinessException(SaleErrorCode.INVALID_STATUS_TRANSITION)
        }
        if (this.status == SaleStatus.ON_SALE && status != SaleStatus.CLOSED) {
            throw BusinessException(SaleErrorCode.INVALID_STATUS_TRANSITION)
        }
    }

    companion object {
        @JvmStatic
        fun builder(): SaleBuilder = SaleBuilder()

        @JvmStatic
        private fun validateSaleInput(
            initialStock: Int?,
            maxPurchaseQuantity: Int?,
            startAt: LocalDateTime?,
            endAt: LocalDateTime?,
        ) {
            if (initialStock == null || initialStock <= 0) {
                throw BusinessException(SaleErrorCode.INVALID_INITIAL_STOCK)
            }
            if (maxPurchaseQuantity == null || maxPurchaseQuantity <= 0) {
                throw BusinessException(SaleErrorCode.INVALID_MAX_PURCHASE_QUANTITY)
            }
            if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
                throw BusinessException(SaleErrorCode.INVALID_SALE_PERIOD)
            }
        }
    }

    class SaleBuilder {
        private var id: Long? = null
        private var goods: Goods? = null
        private var createdBy: Long? = null
        private var initialStock: Int? = null
        private var remainingStock: Int? = null
        private var maxPurchaseQuantity: Int? = null
        private var status: SaleStatus? = null
        private var startAt: LocalDateTime? = null
        private var endAt: LocalDateTime? = null

        fun id(id: Long?): SaleBuilder = apply { this.id = id }

        fun goods(goods: Goods?): SaleBuilder = apply { this.goods = goods }

        fun createdBy(createdBy: Long?): SaleBuilder = apply { this.createdBy = createdBy }

        fun initialStock(initialStock: Int?): SaleBuilder = apply { this.initialStock = initialStock }

        fun remainingStock(remainingStock: Int?): SaleBuilder = apply { this.remainingStock = remainingStock }

        fun maxPurchaseQuantity(maxPurchaseQuantity: Int?): SaleBuilder = apply {
            this.maxPurchaseQuantity = maxPurchaseQuantity
        }

        fun status(status: SaleStatus?): SaleBuilder = apply { this.status = status }

        fun startAt(startAt: LocalDateTime?): SaleBuilder = apply { this.startAt = startAt }

        fun endAt(endAt: LocalDateTime?): SaleBuilder = apply { this.endAt = endAt }

        fun build(): Sale =
            Sale(
                id,
                goods,
                createdBy,
                initialStock,
                remainingStock,
                maxPurchaseQuantity,
                status,
                startAt,
                endAt,
            )
    }
}
