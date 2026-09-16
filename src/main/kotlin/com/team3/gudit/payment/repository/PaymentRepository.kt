package com.team3.gudit.payment.repository

import com.team3.gudit.payment.entity.Payment
import jakarta.persistence.LockModeType
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PaymentRepository : JpaRepository<Payment, Long> {
    fun findByOrderId(orderId: String?): Optional<Payment>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p\nfrom Payment p\nwhere p.orderId = :orderId\n")
    fun findByOrderIdWithLock(@Param("orderId") orderId: String?): Optional<Payment>

    fun findByPaymentKey(paymentKey: String?): Optional<Payment>

    fun findByPurchaseId(purchaseId: Long?): Optional<Payment>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p\nfrom Payment p\nwhere p.purchase.id = :purchaseId\n")
    fun findByPurchaseIdWithLock(@Param("purchaseId") purchaseId: Long?): Optional<Payment>
}
