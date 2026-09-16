package com.team3.gudit.goods.service

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.global.exception.GlobalErrorCode
import com.team3.gudit.goods.domain.entity.Goods
import com.team3.gudit.goods.domain.enums.GoodsStatus
import com.team3.gudit.goods.domain.repository.GoodsRepository
import com.team3.gudit.goods.dto.request.GoodsCreateRequest
import com.team3.gudit.goods.dto.request.GoodsStatusUpdateRequest
import com.team3.gudit.goods.dto.request.GoodsUpdateRequest
import com.team3.gudit.goods.dto.response.GoodsCreateResponse
import com.team3.gudit.goods.dto.response.GoodsDetailResponse
import com.team3.gudit.goods.dto.response.GoodsListResponse
import com.team3.gudit.goods.dto.response.GoodsStatusUpdateResponse
import com.team3.gudit.goods.dto.response.GoodsUpdateResponse
import com.team3.gudit.goods.exception.GoodsErrorCode
import com.team3.gudit.goods.mapper.GoodsMapper
import com.team3.gudit.goods.service.component.ImageStorageManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
class GoodsService(
    private val goodsMapper: GoodsMapper,
    private val goodsRepository: GoodsRepository,
    private val imageStorageManager: ImageStorageManager,
) {
    @Transactional
    fun create(
        request: GoodsCreateRequest,
        fileImage: MultipartFile?,
    ): GoodsCreateResponse {
        log.info(
            "[메뉴 생성 요청] name={}, price={}, description={}, hasImage={}",
            request.name,
            request.price,
            request.description,
            fileImage != null && !fileImage.isEmpty,
        )

        val imageUrl = imageStorageManager.store(fileImage)
        val goods = goodsMapper.toEntity(request, imageUrl)
        val saved = goodsRepository.save(goods)
        return goodsMapper.toCreateResponse(saved)
    }

    @Transactional(readOnly = true)
    fun goodsList(): List<GoodsListResponse> =
        goodsRepository
            .findAllByStatus(GoodsStatus.ACTIVE)
            .map(goodsMapper::toListResponse)

    @Transactional(readOnly = true)
    fun adminGoodsList(): List<GoodsListResponse> =
        goodsRepository
            .findAll()
            .map(goodsMapper::toListResponse)

    @Transactional(readOnly = true)
    fun goodsDetail(id: Long?): GoodsDetailResponse {
        val goods =
            goodsRepository.findByIdAndStatus(requiredId(id), GoodsStatus.ACTIVE)
                .orElseThrow { BusinessException(GoodsErrorCode.GOODS_NOT_FOUND) }
        return goodsMapper.toDetailResponse(goods)
    }

    @Transactional(readOnly = true)
    fun adminGoodsDetail(id: Long?): GoodsDetailResponse {
        val goods =
            goodsRepository.findById(requiredId(id))
                .orElseThrow { BusinessException(GoodsErrorCode.GOODS_NOT_FOUND) }
        return goodsMapper.toDetailResponse(goods)
    }

    @Transactional
    fun updateGoods(
        id: Long?,
        request: GoodsUpdateRequest,
        fileImage: MultipartFile?,
    ): GoodsUpdateResponse {
        val goods = findByIdOrThrow(id)
        var imageUrl = goods.imageUrl

        if (fileImage != null && !fileImage.isEmpty) {
            imageUrl = imageStorageManager.store(fileImage)
            log.debug("[굿즈 이미지 변경] id={}, imageUrl={}", id, imageUrl)
        }

        goodsMapper.updateEntity(goods, request, imageUrl)

        log.info(
            "[굿즈 수정 완료] id={}, name={}, ActiveStatus={}, price={}, description={}",
            goods.id,
            goods.name,
            goods.status,
            goods.price,
            goods.description,
        )

        return goodsMapper.toUpdateResponse(goods)
    }

    @Transactional
    fun updateGoodsStatus(
        id: Long?,
        request: GoodsStatusUpdateRequest,
    ): GoodsStatusUpdateResponse {
        val goods = findByIdOrThrow(id)
        goodsMapper.updateStatus(goods, request)
        return goodsMapper.toStatusUpdateResponse(goods)
    }

    @Transactional
    fun deleteGoods(id: Long?) {
        val goods = findByIdOrThrow(id)
        goods.deactivate()
        log.info("[굿즈 삭제 완료] goodsId={}", id)
    }

    private fun findByIdOrThrow(id: Long?): Goods {
        log.debug("[메뉴 조회] menuId={}", id)
        return goodsRepository.findById(requiredId(id))
            .orElseThrow { BusinessException(GoodsErrorCode.GOODS_NOT_FOUND) }
    }

    private fun requiredId(id: Long?): Long =
        id ?: throw BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE)

    companion object {
        private val log = LoggerFactory.getLogger(GoodsService::class.java)
    }
}
