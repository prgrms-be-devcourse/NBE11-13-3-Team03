package com.team3.gudit.goods.mapper

import com.team3.gudit.goods.domain.entity.Goods
import com.team3.gudit.goods.domain.enums.GoodsStatus
import com.team3.gudit.goods.dto.request.GoodsCreateRequest
import com.team3.gudit.goods.dto.request.GoodsStatusUpdateRequest
import com.team3.gudit.goods.dto.request.GoodsUpdateRequest
import com.team3.gudit.goods.dto.response.GoodsCreateResponse
import com.team3.gudit.goods.dto.response.GoodsDetailResponse
import com.team3.gudit.goods.dto.response.GoodsListResponse
import com.team3.gudit.goods.dto.response.GoodsStatusUpdateResponse
import com.team3.gudit.goods.dto.response.GoodsUpdateResponse
import org.springframework.stereotype.Component

@Component
class GoodsMapper {
    fun toEntity(request: GoodsCreateRequest): Goods = toEntity(request, request.imageUrl)

    fun toEntity(
        request: GoodsCreateRequest,
        imageUrl: String?,
    ): Goods =
        Goods.builder()
            .name(request.name)
            .description(request.description)
            .price(request.price)
            .imageUrl(imageUrl)
            .status(GoodsStatus.ACTIVE)
            .build()

    fun updateEntity(
        goods: Goods,
        request: GoodsUpdateRequest,
        imageUrl: String?,
    ) {
        goods.updateGoodsInfo(
            request.name,
            request.description,
            request.price,
            imageUrl,
        )
    }

    fun updateStatus(
        goods: Goods,
        request: GoodsStatusUpdateRequest,
    ) {
        goods.updateGoodsStatus(request.status)
    }

    fun toCreateResponse(goods: Goods): GoodsCreateResponse =
        GoodsCreateResponse.builder()
            .id(goods.id)
            .name(goods.name)
            .description(goods.description)
            .price(goods.price)
            .imageUrl(goods.imageUrl)
            .status(goods.status)
            .createdAt(goods.createdAt)
            .build()

    fun toListResponse(goods: Goods): GoodsListResponse =
        GoodsListResponse.builder()
            .id(goods.id)
            .name(goods.name)
            .price(goods.price)
            .imageUrl(goods.imageUrl)
            .status(goods.status)
            .createdAt(goods.createdAt)
            .build()

    fun toDetailResponse(goods: Goods): GoodsDetailResponse =
        GoodsDetailResponse.builder()
            .goodsId(goods.id)
            .name(goods.name)
            .description(goods.description)
            .price(goods.price)
            .imageUrl(goods.imageUrl)
            .status(goods.status)
            .createdAt(goods.createdAt)
            .build()

    fun toUpdateResponse(goods: Goods): GoodsUpdateResponse =
        GoodsUpdateResponse.builder()
            .id(goods.id)
            .name(goods.name)
            .description(goods.description)
            .price(goods.price)
            .imageUrl(goods.imageUrl)
            .status(goods.status)
            .build()

    fun toStatusUpdateResponse(goods: Goods): GoodsStatusUpdateResponse =
        GoodsStatusUpdateResponse.builder()
            .id(goods.id)
            .status(goods.status)
            .updatedAt(goods.updatedAt)
            .build()
}
