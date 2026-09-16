package com.team3.gudit.goods.domain.entity

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.global.exception.GlobalErrorCode
import com.team3.gudit.goods.domain.enums.GoodsStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(name = "GOODS")
@EntityListeners(AuditingEntityListener::class)
class Goods protected constructor() {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @field:Column(nullable = false)
    lateinit var name: String
        protected set

    @field:Column(length = 100)
    var description: String? = null
        protected set

    @field:Column(nullable = false)
    var price: Int = 0
        protected set

    var imageUrl: String? = null
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false)
    var status: GoodsStatus = GoodsStatus.ACTIVE
        protected set

    @field:CreatedDate
    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
        protected set

    @field:LastModifiedDate
    @field:Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
        protected set

    protected constructor(
        id: Long?,
        name: String?,
        description: String?,
        price: Int?,
        imageUrl: String?,
        status: GoodsStatus?,
        createdAt: LocalDateTime?,
        updatedAt: LocalDateTime?,
    ) : this() {
        this.id = id
        this.name = name ?: throw BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE)
        this.description = description
        this.price = price ?: throw BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE)
        this.imageUrl = imageUrl
        this.status = status ?: throw BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE)
        this.createdAt = createdAt
        this.updatedAt = updatedAt
    }

    private constructor(
        id: Long?,
        name: String?,
        description: String?,
        price: Int?,
        imageUrl: String?,
        status: GoodsStatus?,
    ) : this() {
        this.id = id
        this.name = name ?: throw BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE)
        this.description = description
        this.price = price ?: throw BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE)
        this.imageUrl = imageUrl
        this.status = status ?: throw BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE)
    }

    fun updateGoodsInfo(
        name: String?,
        description: String?,
        price: Int?,
        imageUrl: String?,
    ) {
        if (name == null || price == null) {
            throw BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE)
        }
        this.name = name
        this.description = description
        this.price = price
        this.imageUrl = imageUrl
    }

    fun updateGoodsStatus(status: GoodsStatus?) {
        this.status = status ?: throw BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE)
    }

    fun deactivate() {
        status = GoodsStatus.INACTIVE
    }

    companion object {
        @JvmStatic
        fun of(
            name: String?,
            description: String?,
            price: Int?,
            imageUrl: String?,
        ): Goods =
            builder()
                .name(name)
                .description(description)
                .price(price)
                .imageUrl(imageUrl)
                .status(GoodsStatus.ACTIVE)
                .build()

        @JvmStatic
        fun builder(): GoodsBuilder = GoodsBuilder()
    }

    class GoodsBuilder {
        private var id: Long? = null
        private var name: String? = null
        private var description: String? = null
        private var price: Int? = null
        private var imageUrl: String? = null
        private var status: GoodsStatus? = null

        fun id(id: Long?): GoodsBuilder = apply { this.id = id }

        fun name(name: String?): GoodsBuilder = apply { this.name = name }

        fun description(description: String?): GoodsBuilder = apply { this.description = description }

        fun price(price: Int?): GoodsBuilder = apply { this.price = price }

        fun imageUrl(imageUrl: String?): GoodsBuilder = apply { this.imageUrl = imageUrl }

        fun status(status: GoodsStatus?): GoodsBuilder = apply { this.status = status }

        fun build(): Goods =
            Goods(
                id,
                name,
                description,
                price,
                imageUrl,
                status,
            )
    }
}
