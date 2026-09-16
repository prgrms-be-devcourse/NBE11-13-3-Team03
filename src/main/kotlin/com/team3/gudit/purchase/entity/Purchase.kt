package com.team3.gudit.purchase.entity

import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.user.domain.entity.User
import jakarta.persistence.*
import java.time.LocalDateTime
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener

@Entity
@Table(name = "purchases")
@EntityListeners(AuditingEntityListener::class)
class Purchase private constructor(user: User, sale: Sale, quantity: Int, purchasePrice: Int) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User = user
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    var sale: Sale = sale
        protected set

    @Column(nullable = false)
    var quantity: Int = quantity
        protected set

    @Column(name = "purchase_price", nullable = false)
    var purchasePrice: Int = purchasePrice
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PurchaseStatus = PurchaseStatus.PENDING_PAYMENT
        protected set

    @Column(name = "purchased_at")
    var purchasedAt: LocalDateTime? = null
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

    companion object {
        @JvmStatic
        fun create(user: User, sale: Sale, quantity: Int, purchasePrice: Int): Purchase =
        Purchase(user, sale, quantity, purchasePrice)
    }

    fun complete() {
        this.status = PurchaseStatus.PURCHASED
        this.purchasedAt = LocalDateTime.now()
    }

    fun cancel() {
        this.status = PurchaseStatus.CANCELED
        this.canceledAt = LocalDateTime.now()
    }
}
