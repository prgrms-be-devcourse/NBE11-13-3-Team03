package com.team3.gudit.goods.domain.repository

import com.team3.gudit.goods.domain.entity.Goods
import com.team3.gudit.goods.domain.enums.GoodsStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface GoodsRepository : JpaRepository<Goods, Long> {
    fun findAllByStatus(status: GoodsStatus): List<Goods>

    fun findByIdAndStatus(
        id: Long,
        status: GoodsStatus,
    ): Optional<Goods>
}
