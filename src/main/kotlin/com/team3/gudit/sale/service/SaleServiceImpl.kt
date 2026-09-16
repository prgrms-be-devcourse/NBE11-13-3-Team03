package com.team3.gudit.sale.service

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.global.exception.GlobalErrorCode
import com.team3.gudit.goods.domain.enums.GoodsStatus
import com.team3.gudit.goods.domain.repository.GoodsRepository
import com.team3.gudit.goods.exception.GoodsErrorCode
import com.team3.gudit.purchase.entity.PurchaseStatus
import com.team3.gudit.purchase.repository.PurchaseRepository
import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.sale.domain.enums.SaleStatus
import com.team3.gudit.sale.domain.repository.SaleRepository
import com.team3.gudit.sale.dto.SaleRedisDto
import com.team3.gudit.sale.dto.reqeust.SaleCreateRequestDto
import com.team3.gudit.sale.dto.reqeust.SaleStatusUpdateRequestDto
import com.team3.gudit.sale.dto.reqeust.SaleUpdateRequestDto
import com.team3.gudit.sale.dto.response.SaleCreateResponseDto
import com.team3.gudit.sale.dto.response.SaleDetailResponseDto
import com.team3.gudit.sale.dto.response.SaleListResponseDto
import com.team3.gudit.sale.dto.response.SaleStatusUpdateResponseDto
import com.team3.gudit.sale.exception.SaleErrorCode
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

@Service
@Transactional
class SaleServiceImpl(
    private val saleRepository: SaleRepository,
    private val goodsRepository: GoodsRepository,
    private val redisTemplate: StringRedisTemplate,
    private val purchaseRepository: PurchaseRepository,
) : SaleService {
    @Transactional
    override fun createSale(request: SaleCreateRequestDto): SaleCreateResponseDto {
        val goods =
            goodsRepository.findById(requiredId(request.goodsId))
                .orElseThrow { BusinessException(GoodsErrorCode.GOODS_NOT_FOUND) }

        val sale = request.toEntity(goods)
        val savedSale = saleRepository.save(sale)

        return SaleCreateResponseDto.from(savedSale)
    }

    @Transactional(readOnly = true)
    override fun saleDetail(id: Long?): SaleDetailResponseDto {
        val sale =
            saleRepository.findById(requiredId(id))
                .orElseThrow { BusinessException(SaleErrorCode.SALE_NOT_FOUND) }

        // 판매 중일 때만 Redis 실시간 재고 사용
        if (sale.status == SaleStatus.ON_SALE) {
            val redisStock = getRedisStock(id)

            if (redisStock != null) {
                val displayStatus = resolveDisplayStatus(sale, redisStock)
                return SaleDetailResponseDto.from(sale, redisStock, displayStatus)
            }
        }

        // READY / CLOSED 또는 Redis Key가 없는 경우 RDB 값 사용
        val remainingStock = sale.remainingStock
        return SaleDetailResponseDto.from(
            sale,
            remainingStock,
            resolveDisplayStatus(sale, remainingStock),
        )
    }

    @Transactional(readOnly = true)
    override fun saleList(): List<SaleListResponseDto> =
        saleRepository.findAllByGoods_Status(GoodsStatus.ACTIVE)
            .map { sale ->
                if (sale.status == SaleStatus.ON_SALE) {
                    val redisStock = getRedisStock(sale.id)

                    if (redisStock != null) {
                        return@map SaleListResponseDto.from(
                            sale,
                            redisStock,
                            resolveDisplayStatus(sale, redisStock),
                        )
                    }
                }

                SaleListResponseDto.from(
                    sale,
                    sale.remainingStock,
                    resolveDisplayStatus(sale, sale.remainingStock),
                )
            }

    @Transactional
    override fun updateSale(
        saleId: Long?,
        request: SaleUpdateRequestDto,
    ): SaleDetailResponseDto {
        val sale =
            saleRepository.findById(requiredId(saleId))
                .orElseThrow { BusinessException(SaleErrorCode.SALE_NOT_FOUND) }

        // READY(판매 대기) 상태에서만 수정 가능 (엔티티 내 validateModifiable 수행)
        sale.updateSaleInfo(
            request.initialStock,
            request.maxPurchaseQuantity,
            request.startAt,
            request.endAt,
        )

        // 변경된 stock/info를 다시 적재하고 TTL도 다시 설정
        warmupSaleInfo(saleId)

        return SaleDetailResponseDto.from(sale)
    }

    @Transactional
    override fun updateSaleStatus(
        saleId: Long?,
        request: SaleStatusUpdateRequestDto,
    ): SaleStatusUpdateResponseDto {
        val sale =
            saleRepository.findById(requiredId(saleId))
                .orElseThrow { BusinessException(SaleErrorCode.SALE_NOT_FOUND) }

        // 이미 삭제(DELETED)되었거나 종료(CLOSED)된 상품은 상태를 변경할 수 없음
        sale.updateSaleStatus(request.status)

        return SaleStatusUpdateResponseDto.from(sale)
    }

    // 비상 시 판매 중지로 상태 값 변경하는 메서드
    @Transactional
    fun closeSale(saleId: Long?) {
        // 1. DB 조회 및 상태 검증
        val sale =
            saleRepository.findById(requiredId(saleId))
                .orElseThrow { BusinessException(SaleErrorCode.SALE_NOT_FOUND) }

        // 2. DB 상태를 CLOSED로 변경
        sale.updateSaleStatus(SaleStatus.CLOSED)

        // 3. Redis status 즉시 동기화 (Fast-Fail 차단용)
        val infoKey = "sale:$saleId:info"
        redisTemplate.opsForHash<String, String>().put(infoKey, "status", SaleStatus.CLOSED.name)
    }

    @Transactional
    override fun deleteSale(id: Long?) {
        val sale =
            saleRepository.findById(requiredId(id))
                .orElseThrow { BusinessException(SaleErrorCode.SALE_NOT_FOUND) }

        // 진행 중인 판매 상품은 삭제 불가 (엔티티 내부에서 ON_SALE 상태 검증)
        // 삭제 후 상태 값 변경 불가
        // RDB: 데이터 이력 보존 및 어드민 조회를 위해 Soft Delete 처리
        sale.deleteSale()

        // Redis: 삭제된 상품의 재고 Key가 메모리를 차지하지 않도록 즉시 삭제
        val stockKey = "sale:$id:stock"
        val infoKey = "sale:$id:info"
        redisTemplate.delete(listOf(stockKey, infoKey))
    }

    @Transactional
    override fun warmupSaleInfo(id: Long?) {
        val sale =
            saleRepository.findById(requiredId(id))
                .orElseThrow { BusinessException(SaleErrorCode.SALE_NOT_FOUND) }

        // Warm-up은 판매 시작 전 Redis 데이터를 준비하는 용도이므로
        // READY 상태에서만 허용한다.
        if (sale.status != SaleStatus.READY) {
            throw BusinessException(SaleErrorCode.CANNOT_WARMUP_NON_READY_SALE)
        }

        val stockKey = "sale:$id:stock"
        val infoKey = "sale:$id:info"

        // 재고 캐싱
        redisTemplate.opsForValue().set(stockKey, sale.remainingStock.toString())

        // 판매 정보 캐싱
        val dto = SaleRedisDto.from(sale)
        redisTemplate.opsForHash<String, String>().putAll(infoKey, dto.toHashFields())

        // 판매 종료 후 2일까지 유지
        val endAt = sale.endAt
        val expireAt = endAt.plusDays(2)
        val ttl = Duration.between(LocalDateTime.now(), expireAt)

        if (ttl.isNegative || ttl.isZero) {
            redisTemplate.delete(listOf(stockKey, infoKey))
            return
        }

        redisTemplate.expire(stockKey, ttl)
        redisTemplate.expire(infoKey, ttl)
    }

    @Transactional
    override fun startSale(id: Long?) {
        val sale =
            saleRepository.findById(requiredId(id))
                .orElseThrow { BusinessException(SaleErrorCode.SALE_NOT_FOUND) }

        // DB: READY -> ON_SALE
        sale.updateSaleStatus(SaleStatus.ON_SALE)

        // Redis에서도 구매 가능 상태로 전환
        val infoKey = "sale:$id:info"
        redisTemplate.opsForHash<String, String>().put(
            infoKey,
            "status",
            SaleStatus.ON_SALE.name,
        )
    }

    @Transactional
    override fun endSale(id: Long?) {
        val sale =
            saleRepository.findById(requiredId(id))
                .orElseThrow { BusinessException(SaleErrorCode.SALE_NOT_FOUND) }

        val stockKey = "sale:$id:stock"
        val infoKey = "sale:$id:info"

        // 1. Redis에서 먼저 신규 구매 차단
        // Redis 연결 자체가 실패하면 예외를 유지해 스케줄러가 재시도한다.
        redisTemplate.opsForHash<String, String>().put(
            infoKey,
            "status",
            SaleStatus.CLOSED.name,
        )

        // 2. 구매 차단 후 Redis 최종 재고 조회
        // 연결 오류는 여기서 그대로 전파하고, Key 누락만 구분해서 처리한다.
        val redisStock = redisTemplate.opsForValue().get(stockKey)

        if (redisStock == null) {
            // Redis 재고를 알 수 없으므로 기존 RDB 재고를 덮어쓰지 않는다.
            // finalStockSyncedAt도 null로 유지해 동기화 미완료 상태로 남긴다.
            log.error(
                "[판매 종료 재고 동기화 보류] Redis stock Key가 없습니다. " +
                    "saleId={}, stockKey={}, RemainingStock={}",
                id,
                stockKey,
                sale.remainingStock,
            )
        } else {
            try {
                val finalRemainingStock = Integer.parseInt(redisStock)

                // Redis 재고가 정상인 경우에만 RDB 1차 동기화
                sale.syncRemainingStock(finalRemainingStock)

                log.info(
                    "[판매 종료 재고 1차 동기화 완료] saleId={}, remainingStock={}",
                    id,
                    finalRemainingStock,
                )
            } catch (exception: NumberFormatException) {
                // 잘못된 Redis 값으로 RDB 재고를 덮어쓰지 않는다.
                // 판매 종료는 계속 진행하고 관리자 확인 대상으로 남긴다.
                log.error(
                    "[판매 종료 재고 동기화 보류] Redis stock 값이 올바르지 않습니다. " +
                        "saleId={}, stockKey={}, redisStock={}",
                    id,
                    stockKey,
                    redisStock,
                    exception,
                )
            }
        }

        // 재고 동기화 성공 여부와 관계없이 DB 판매 종료
        // 신규 구매는 Redis에서 이미 차단했으며,
        // finalStockSyncedAt은 최종 동기화 스케줄러가 성공할 때 기록한다.
        sale.updateSaleStatus(SaleStatus.CLOSED)
    }

    @Transactional
    override fun syncFinalRemainingStock(saleId: Long?): Boolean {
        val sale =
            saleRepository.findByIdWithLock(saleId)
                .orElseThrow { BusinessException(SaleErrorCode.SALE_NOT_FOUND) }

        // 종료된 판매만 처리
        if (sale.status != SaleStatus.CLOSED) {
            return false
        }

        // 이미 최종 동기화한 판매는 처리하지 않음
        if (sale.finalStockSyncedAt != null) {
            return false
        }

        val endAt = sale.endAt
        val cancellationDeadline = endAt.plusDays(1)

        // Redis user key가 살아 있고 취소 가능한 기간이면 보류
        if (LocalDateTime.now().isBefore(cancellationDeadline)) {
            return false
        }

        // 결제 실패나 timeout으로 복구될 구매가 남아 있으면 보류
        val hasPendingPurchase =
            purchaseRepository.existsBySaleIdAndStatus(
                saleId,
                PurchaseStatus.PENDING_PAYMENT,
            )

        if (hasPendingPurchase) {
            return false
        }

        val stockKey = "sale:$saleId:stock"
        val redisStock = redisTemplate.opsForValue().get(stockKey)

        // Redis stock은 endAt + 2일까지 존재해야 함
        if (redisStock == null) {
            return false
        }

        val finalRemainingStock =
            try {
                Integer.parseInt(redisStock)
            } catch (exception: NumberFormatException) {
                throw BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR)
            }

        sale.syncRemainingStock(finalRemainingStock)
        sale.completeFinalStockSync()

        return true
    }

    private fun getRedisStock(saleId: Long?): Int? {
        val stockKey = "sale:$saleId:stock"
        val value = redisTemplate.opsForValue().get(stockKey) ?: return null

        return try {
            Integer.parseInt(value)
        } catch (exception: NumberFormatException) {
            null
        }
    }

    // 응답용 status
    private fun resolveDisplayStatus(
        sale: Sale,
        currentStock: Int?,
    ): SaleStatus? {
        // 조회 응답에서는 즉시 CLOSED로 표시
        val endAt = sale.endAt
        if (sale.status == SaleStatus.ON_SALE && !LocalDateTime.now().isBefore(endAt)) {
            return SaleStatus.CLOSED
        }

        // SOLD_OUT은 RDB 상태값이 아니라 Redis 실시간 재고 기반으로 계산
        if (sale.status == SaleStatus.ON_SALE && currentStock != null && currentStock <= 0) {
            return SaleStatus.SOLD_OUT
        }

        return sale.status
    }

    private fun requiredId(id: Long?): Long =
        id ?: throw IllegalArgumentException("The given id must not be null")

    companion object {
        private val log = LoggerFactory.getLogger(SaleServiceImpl::class.java)
    }
}
