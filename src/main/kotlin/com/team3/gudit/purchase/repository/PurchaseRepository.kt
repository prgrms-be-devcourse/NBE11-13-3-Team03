package com.team3.gudit.purchase.repository

import com.team3.gudit.purchase.entity.Purchase
import com.team3.gudit.purchase.entity.PurchaseStatus
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PurchaseRepository : JpaRepository<Purchase, Long> {

    fun existsByUserIdAndSaleIdAndStatusNot(userId: Long?, saleId: Long?, status: PurchaseStatus?): Boolean

    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long?): List<Purchase>

    fun findByIdAndUserId(purchaseId: Long?, userId: Long?): Optional<Purchase>

    fun findAllByStatusAndCreatedAtBefore(status: PurchaseStatus?, createdAt: LocalDateTime?): List<Purchase>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p\nfrom Purchase p\nwhere p.id = :purchaseId\n")
    fun findByIdWithLock(
        @Param("purchaseId") purchaseId: Long?
    ): Optional<Purchase>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p\nfrom Purchase p\nwhere p.id = :purchaseId\n  and p.user.id = :userId\n")
    fun findByIdAndUserIdWithLock(
        @Param("purchaseId") purchaseId: Long?,
        @Param("userId") userId: Long?
    ): Optional<Purchase>

    fun existsBySaleIdAndStatus(saleId: Long?, status: PurchaseStatus?): Boolean
}
