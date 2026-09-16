package com.team3.gudit.sale.domain.repository

import com.team3.gudit.goods.domain.enums.GoodsStatus
import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.sale.domain.enums.SaleStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional

interface SaleRepository : JpaRepository<Sale, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sale s WHERE s.id = :id")
    fun findByIdWithLock(
        @Param("id") id: Long?,
    ): Optional<Sale>

    fun findByStatusAndStartAtBetween(
        status: SaleStatus,
        startAtAfter: LocalDateTime,
        startAtBefore: LocalDateTime,
    ): List<Sale>

    fun findByStatusAndStartAtLessThanEqualAndEndAtGreaterThan(
        status: SaleStatus,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
    ): List<Sale>

    fun findByStatusAndEndAtLessThanEqual(
        status: SaleStatus,
        endAt: LocalDateTime,
    ): List<Sale>

    fun findByStatusAndFinalStockSyncedAtIsNullAndEndAtLessThanEqual(
        status: SaleStatus,
        endAt: LocalDateTime,
    ): List<Sale>

    @EntityGraph(attributePaths = ["goods"])
    fun findAllByGoods_Status(goodsStatus: GoodsStatus): List<Sale>
}
