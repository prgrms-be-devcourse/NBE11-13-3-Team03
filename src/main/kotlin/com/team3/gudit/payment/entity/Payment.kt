package com.team3.gudit.payment.entity

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.payment.exception.PaymentErrorCode
import com.team3.gudit.purchase.entity.Purchase
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
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import java.util.UUID
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener

@Entity
@Table(
    name = "payments",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_payment_purchase", columnNames = ["purchase_id"]),
        UniqueConstraint(name = "uk_payment_order_id", columnNames = ["order_id"]),
        UniqueConstraint(name = "uk_payment_payment_key", columnNames = ["payment_key"])
    ]
)
@EntityListeners(AuditingEntityListener::class)
class Payment private constructor(purchase: Purchase, amount: Int) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false)
    var purchase: Purchase = purchase
        protected set

    @Column(name = "order_id", nullable = false, length = 64)
    var orderId: String = "GUDIT_${UUID.randomUUID()}"
        protected set

    @Column(name = "payment_key")
    var paymentKey: String? = null
        protected set

    @Column(nullable = false)
    var amount: Int = amount
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatus = PaymentStatus.READY
        protected set

    @Column(name = "approved_at")
    var approvedAt: LocalDateTime? = null
        protected set

    @Column(name = "canceled_at")
    var canceledAt: LocalDateTime? = null
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
        protected set

    fun start(paymentKey: String?) {
        validateStatus(PaymentStatus.READY)
        this.paymentKey = paymentKey
        status = PaymentStatus.IN_PROGRESS
    }

    fun complete(approvedAt: LocalDateTime) {
        validateStatus(PaymentStatus.IN_PROGRESS)
        status = PaymentStatus.DONE
        this.approvedAt = approvedAt
    }

    fun fail() {
        validateStatus(PaymentStatus.IN_PROGRESS)
        status = PaymentStatus.FAILED
    }

    fun cancel() {
        validateStatus(PaymentStatus.DONE)
        status = PaymentStatus.CANCELED
        canceledAt = LocalDateTime.now()
    }

    fun cancelAfterApprovalFailure() {
        validateStatus(PaymentStatus.IN_PROGRESS)
        status = PaymentStatus.CANCELED
        canceledAt = LocalDateTime.now()
    }

    fun cancelReady() {
        validateStatus(PaymentStatus.READY)
        status = PaymentStatus.CANCELED
        canceledAt = LocalDateTime.now()
    }

    private fun validateStatus(expectedStatus: PaymentStatus) {
        if (status != expectedStatus) {
            throw BusinessException(
                PaymentErrorCode.INVALID_PAYMENT_STATUS,
                "Invalid payment status transition. current=$status, expected=$expectedStatus"
            )
        }
    }

    fun completeByWebhook(paymentKey: String?, approvedAt: LocalDateTime) {
        if (status != PaymentStatus.READY && status != PaymentStatus.IN_PROGRESS) {
            throw BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS)
        }
        this.paymentKey = paymentKey
        status = PaymentStatus.DONE
        this.approvedAt = approvedAt
    }

    fun failByWebhook() {
        if (status != PaymentStatus.READY && status != PaymentStatus.IN_PROGRESS) {
            throw BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS)
        }
        status = PaymentStatus.FAILED
    }

    fun cancelByWebhook() {
        if (status != PaymentStatus.READY &&
            status != PaymentStatus.IN_PROGRESS &&
            status != PaymentStatus.DONE
        ) {
            throw BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS)
        }
        status = PaymentStatus.CANCELED
        canceledAt = LocalDateTime.now()
    }

    companion object {
        @JvmStatic
        fun create(purchase: Purchase, amount: Int): Payment = Payment(purchase, amount)
    }
}
