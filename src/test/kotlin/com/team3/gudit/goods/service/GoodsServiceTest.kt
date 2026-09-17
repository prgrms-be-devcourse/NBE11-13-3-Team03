package com.team3.gudit.goods.service

import com.team3.gudit.global.exception.BusinessException
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
import com.team3.gudit.goods.mapper.GoodsMapper
import com.team3.gudit.goods.service.component.ImageStorageManager
import com.team3.gudit.testsupport.anyValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mock.web.MockMultipartFile
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class GoodsServiceTest {
    @InjectMocks
    private lateinit var goodsService: GoodsService

    @Mock
    private lateinit var goodsMapper: GoodsMapper

    @Mock
    private lateinit var goodsRepository: GoodsRepository

    @Mock
    private lateinit var imageStorageManager: ImageStorageManager

    @Nested
    @DisplayName("굿즈 생성 (create)")
    inner class Create {
        @Test
        @DisplayName("이미지와 함께 굿즈 생성 요청 시 정상적으로 생성된다.")
        fun createSuccess() {
            val request = GoodsCreateRequest("굿즈A", "설명A", 10_000, null)
            val file = MockMultipartFile("fileImage", "test.png", "image/png", "test image contents".toByteArray())
            val storedImageUrl = "/thumbnails/stored-test.png"
            val goods = goods(1L, "굿즈A", storedImageUrl)
            val savedGoods = goods(1L, "굿즈A", storedImageUrl)
            val response =
                GoodsCreateResponse.builder()
                    .id(1L)
                    .name("굿즈A")
                    .price(10_000)
                    .description("설명A")
                    .imageUrl(storedImageUrl)
                    .build()
            given(imageStorageManager.store(file)).willReturn(storedImageUrl)
            given(goodsMapper.toEntity(request, storedImageUrl)).willReturn(goods)
            given(goodsRepository.save(goods)).willReturn(savedGoods)
            given(goodsMapper.toCreateResponse(savedGoods)).willReturn(response)

            val result = goodsService.create(request, file)

            assertThat(result.id).isEqualTo(1L)
            assertThat(result.name).isEqualTo("굿즈A")
            verify(imageStorageManager).store(file)
            verify(goodsRepository).save(goods)
        }
    }

    @Nested
    @DisplayName("굿즈 목록 조회 (goodsList)")
    inner class GoodsList {
        @Test
        @DisplayName("상태가 ACTIVE인 굿즈 목록을 조회한다.")
        fun goodsListSuccess() {
            val goods1 = goods(1L, "굿즈A")
            val goods2 = goods(2L, "굿즈B")
            val response1 = GoodsListResponse.builder().id(1L).name("굿즈A").build()
            val response2 = GoodsListResponse.builder().id(2L).name("굿즈B").build()
            given(goodsRepository.findAllByStatus(GoodsStatus.ACTIVE)).willReturn(listOf(goods1, goods2))
            given(goodsMapper.toListResponse(goods1)).willReturn(response1)
            given(goodsMapper.toListResponse(goods2)).willReturn(response2)

            val result = goodsService.goodsList()

            assertThat(result).hasSize(2)
            assertThat(result[0].name).isEqualTo("굿즈A")
            assertThat(result[1].name).isEqualTo("굿즈B")
        }
    }

    @Nested
    @DisplayName("굿즈 상세 조회 (goodsDetail)")
    inner class GoodsDetail {
        @Test
        @DisplayName("ACTIVE 상태의 굿즈가 존재하면 상세 정보를 반환한다.")
        fun goodsDetailSuccess() {
            val goods = goods(1L, "굿즈A")
            val response = GoodsDetailResponse.builder().goodsId(1L).name("굿즈A").build()
            given(goodsRepository.findByIdAndStatus(1L, GoodsStatus.ACTIVE)).willReturn(Optional.of(goods))
            given(goodsMapper.toDetailResponse(goods)).willReturn(response)

            val result = goodsService.goodsDetail(1L)

            assertThat(result.goodsId).isEqualTo(1L)
        }

        @Test
        @DisplayName("존재하지 않거나 INACTIVE인 굿즈 조회 시 예외가 발생한다.")
        fun goodsDetailNotFoundThrowsException() {
            given(goodsRepository.findByIdAndStatus(999L, GoodsStatus.ACTIVE)).willReturn(Optional.empty())

            assertThatThrownBy { goodsService.goodsDetail(999L) }
                .isInstanceOf(BusinessException::class.java)
        }

        @Test
        @DisplayName("굿즈 ID가 null이면 저장소를 조회하지 않고 예외가 발생한다.")
        fun goodsDetailNullIdThrowsException() {
            assertThatThrownBy { goodsService.goodsDetail(null) }
                .isInstanceOf(BusinessException::class.java)

            verify(goodsRepository, never()).findByIdAndStatus(anyLong(), anyValue(GoodsStatus::class.java))
        }
    }

    @Nested
    @DisplayName("굿즈 수정 (updateGoods)")
    inner class UpdateGoods {
        @Test
        @DisplayName("새로운 이미지와 함께 수정 요청 시 이미지 저장 후 굿즈 정보를 수정한다.")
        fun updateGoodsWithNewImageSuccess() {
            val request = GoodsUpdateRequest("수정된 굿즈", "수정된 설명", 15_000, null)
            val newFile = MockMultipartFile("fileImage", "new.png", "image/png", "new image".toByteArray())
            val goods = goods(1L, "기존 굿즈", "/thumbnails/old.png")
            val response = GoodsUpdateResponse.builder().id(1L).name("수정된 굿즈").build()
            given(goodsRepository.findById(1L)).willReturn(Optional.of(goods))
            given(imageStorageManager.store(newFile)).willReturn("/thumbnails/new.png")
            given(goodsMapper.toUpdateResponse(goods)).willReturn(response)

            val result = goodsService.updateGoods(1L, request, newFile)

            assertThat(result).isNotNull
            verify(imageStorageManager).store(newFile)
            verify(goodsMapper).updateEntity(goods, request, "/thumbnails/new.png")
        }

        @Test
        @DisplayName("이미지 없이 수정 요청 시 기존 이미지를 유지하며 수정한다.")
        fun updateGoodsWithoutImageSuccess() {
            val request = GoodsUpdateRequest("수정된 굿즈", "수정된 설명", 15_000, null)
            val goods = goods(1L, "기존 굿즈", "/thumbnails/old.png")
            val response = GoodsUpdateResponse.builder().id(1L).name("수정된 굿즈").build()
            given(goodsRepository.findById(1L)).willReturn(Optional.of(goods))
            given(goodsMapper.toUpdateResponse(goods)).willReturn(response)

            val result = goodsService.updateGoods(1L, request, null)

            assertThat(result).isNotNull
            verify(imageStorageManager, never()).store(any())
            verify(goodsMapper).updateEntity(goods, request, "/thumbnails/old.png")
        }
    }

    @Nested
    @DisplayName("굿즈 상태 수정 (updateGoodsStatus)")
    inner class UpdateGoodsStatus {
        @Test
        @DisplayName("굿즈 상태를 정상적으로 변경한다.")
        fun updateGoodsStatusSuccess() {
            val request = GoodsStatusUpdateRequest(GoodsStatus.INACTIVE)
            val goods = goods(1L, "테스트 상품")
            val response = GoodsStatusUpdateResponse.builder().id(1L).status(GoodsStatus.INACTIVE).build()
            given(goodsRepository.findById(1L)).willReturn(Optional.of(goods))
            given(goodsMapper.toStatusUpdateResponse(goods)).willReturn(response)

            val result = goodsService.updateGoodsStatus(1L, request)

            assertThat(result).isNotNull
            verify(goodsMapper).updateStatus(goods, request)
        }
    }

    private fun goods(
        id: Long,
        name: String,
        imageUrl: String? = null,
    ): Goods =
        Goods.builder()
            .id(id)
            .name(name)
            .price(10_000)
            .description("설명A")
            .imageUrl(imageUrl)
            .status(GoodsStatus.ACTIVE)
            .build()
}
