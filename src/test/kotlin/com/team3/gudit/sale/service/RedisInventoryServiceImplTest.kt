package com.team3.gudit.sale.service

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.global.exception.ErrorCode
import com.team3.gudit.global.exception.GlobalErrorCode
import com.team3.gudit.sale.domain.repository.SaleRepository
import com.team3.gudit.sale.exception.SaleErrorCode
import com.team3.gudit.sale.metrics.InventoryMetrics
import com.team3.gudit.testsupport.anyValue
import com.team3.gudit.testsupport.eqValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import java.util.stream.Stream

@ExtendWith(MockitoExtension::class)
class RedisInventoryServiceImplTest {
    @Mock
    private lateinit var redisTemplate: StringRedisTemplate

    @Mock
    private lateinit var stockDecrementScript: DefaultRedisScript<Long>

    @Mock
    private lateinit var stockRestoreScript: DefaultRedisScript<Long>

    @Mock
    private lateinit var stockRestoreIdempotentScript: DefaultRedisScript<Long>

    @Mock
    private lateinit var saleRepository: SaleRepository

    @Mock
    private lateinit var inventoryMetrics: InventoryMetrics

    private lateinit var inventoryService: RedisInventoryServiceImpl

    @BeforeEach
    fun setUp() {
        inventoryService =
            RedisInventoryServiceImpl(
                redisTemplate,
                stockDecrementScript,
                stockRestoreScript,
                stockRestoreIdempotentScript,
                saleRepository,
                inventoryMetrics,
            )
    }

    @Test
    @DisplayName("Lua 재고 차감 결과가 1이면 정상 처리한다")
    fun decreaseStockSuccess() {
        val expectedKeys = listOf("sale:1:stock", "sale:1:info", "sale:1:user:10")
        given(redisTemplate.execute(eqValue(stockDecrementScript), eqValue(expectedKeys), anyValue(Array<Any>::class.java))).willReturn(1L)

        assertThatCode { inventoryService.decreaseStock(1L, 10L, 2) }.doesNotThrowAnyException()

        val argumentsCaptor = objectArrayCaptor()
        verify(redisTemplate).execute(eqValue(stockDecrementScript), eqValue(expectedKeys), argumentsCaptor.capture())
        val arguments = argumentsCaptor.value
        assertThat(arguments).hasSize(2)
        assertThat(arguments[0]).isEqualTo("2")
        assertThat(arguments[1]).isInstanceOf(String::class.java)
        assertThat((arguments[1] as String).toLong()).isPositive()
    }

    @ParameterizedTest(name = "Lua 결과 {0}이면 {1} 예외가 발생한다")
    @MethodSource("decrementErrorCodes")
    @DisplayName("Lua 재고 차감 오류 코드를 비즈니스 예외로 변환한다")
    fun decreaseStockScriptError(
        scriptResult: Long,
        expectedErrorCode: SaleErrorCode,
    ) {
        val keys = listOf("sale:1:stock", "sale:1:info", "sale:1:user:10")
        given(redisTemplate.execute(eqValue(stockDecrementScript), eqValue(keys), anyValue(Array<Any>::class.java))).willReturn(scriptResult)

        assertBusinessError(expectedErrorCode) { inventoryService.decreaseStock(1L, 10L, 1) }
    }

    @Test
    @DisplayName("Lua 재고 차감 결과가 null이면 내부 서버 오류가 발생한다")
    fun decreaseStockNullResult() {
        val keys = listOf("sale:1:stock", "sale:1:info", "sale:1:user:10")
        given(redisTemplate.execute(eqValue(stockDecrementScript), eqValue(keys), anyValue(Array<Any>::class.java))).willReturn(null)

        assertBusinessError(GlobalErrorCode.INTERNAL_SERVER_ERROR) {
            inventoryService.decreaseStock(1L, 10L, 1)
        }
    }

    @Test
    @DisplayName("재고 차감 수량이 0 이하이면 Redis를 호출하지 않고 예외가 발생한다")
    fun decreaseStockWithInvalidQuantity() {
        listOf(0, -1).forEach { quantity ->
            assertBusinessError(SaleErrorCode.INVALID_PURCHASE_QUANTITY) {
                inventoryService.decreaseStock(1L, 10L, quantity)
            }
        }
        verifyNoInteractions(redisTemplate)
    }

    @Test
    @DisplayName("Lua 재고 복구 결과가 양수이면 정상 처리한다")
    fun restoreStockSuccess() {
        val keys = listOf("sale:1:stock", "sale:1:user:10")
        given(redisTemplate.execute(eqValue(stockRestoreScript), eqValue(keys), anyValue(Array<Any>::class.java))).willReturn(2L)

        assertThatCode { inventoryService.restoreStock(1L, 10L, 2) }.doesNotThrowAnyException()

        val argumentsCaptor = objectArrayCaptor()
        verify(redisTemplate).execute(eqValue(stockRestoreScript), eqValue(keys), argumentsCaptor.capture())
        assertThat(argumentsCaptor.value).containsExactly("2")
    }

    @Test
    @DisplayName("이미 복구되어 Lua 결과가 0이면 중복 복구 없이 정상 종료한다")
    fun restoreStockAlreadyRestored() {
        val keys = listOf("sale:1:stock", "sale:1:user:10")
        given(redisTemplate.execute(eqValue(stockRestoreScript), eqValue(keys), anyValue(Array<Any>::class.java))).willReturn(0L)

        assertThatCode { inventoryService.restoreStock(1L, 10L, 1) }.doesNotThrowAnyException()
        verify(redisTemplate).execute(eqValue(stockRestoreScript), eqValue(keys), anyValue(Array<Any>::class.java))
    }

    @Test
    @DisplayName("Lua 재고 복구 결과가 null이면 내부 서버 오류가 발생한다")
    fun restoreStockNullResult() {
        val keys = listOf("sale:1:stock", "sale:1:user:10")
        given(redisTemplate.execute(eqValue(stockRestoreScript), eqValue(keys), anyValue(Array<Any>::class.java))).willReturn(null)

        assertBusinessError(GlobalErrorCode.INTERNAL_SERVER_ERROR) {
            inventoryService.restoreStock(1L, 10L, 1)
        }
    }

    @Test
    @DisplayName("재고 복구 수량이 0 이하이면 Redis를 호출하지 않고 예외가 발생한다")
    fun restoreStockWithInvalidQuantity() {
        listOf(0, -1).forEach { quantity ->
            assertBusinessError(SaleErrorCode.INVALID_PURCHASE_QUANTITY) {
                inventoryService.restoreStock(1L, 10L, quantity)
            }
        }
        verifyNoInteractions(redisTemplate)
    }

    @Test
    @DisplayName("Redis stock Key가 없어 Lua 결과가 -1이면 재고 정보 누락 예외가 발생한다")
    fun restoreStockWithoutStockKey() {
        val keys = listOf("sale:1:stock", "sale:1:user:10")
        given(redisTemplate.execute(eqValue(stockRestoreScript), eqValue(keys), anyValue(Array<Any>::class.java))).willReturn(-1L)

        assertBusinessError(SaleErrorCode.REDIS_STOCK_NOT_FOUND) {
            inventoryService.restoreStock(1L, 10L, 1)
        }
        verify(redisTemplate).execute(eqValue(stockRestoreScript), eqValue(keys), anyValue(Array<Any>::class.java))
    }

    @Test
    @DisplayName("멱등 재고 복구 시 eventId를 포함한 Key와 TTL을 Lua Script에 전달한다")
    fun restoreStockIdempotentlySuccess() {
        val keys = listOf("sale:1:stock", "sale:1:user:10", "stock-restore:processed:event-123")
        given(redisTemplate.execute(eqValue(stockRestoreIdempotentScript), eqValue(keys), anyValue(Array<Any>::class.java))).willReturn(2L)

        assertThatCode {
            inventoryService.restoreStockIdempotently("event-123", 1L, 10L, 2)
        }.doesNotThrowAnyException()

        val argumentsCaptor = objectArrayCaptor()
        verify(redisTemplate).execute(eqValue(stockRestoreIdempotentScript), eqValue(keys), argumentsCaptor.capture())
        assertThat(argumentsCaptor.value).containsExactly("2", "86400")
    }

    @Test
    @DisplayName("이미 처리한 eventId로 Lua 결과가 0이면 중복 복구 없이 정상 종료한다")
    fun restoreStockIdempotentlyAlreadyProcessed() {
        val keys = listOf("sale:1:stock", "sale:1:user:10", "stock-restore:processed:event-123")
        given(redisTemplate.execute(eqValue(stockRestoreIdempotentScript), eqValue(keys), anyValue(Array<Any>::class.java))).willReturn(0L)

        assertThatCode {
            inventoryService.restoreStockIdempotently("event-123", 1L, 10L, 1)
        }.doesNotThrowAnyException()
    }

    @Test
    @DisplayName("멱등 재고 복구 결과가 null이면 내부 서버 오류가 발생한다")
    fun restoreStockIdempotentlyNullResult() {
        val keys = listOf("sale:1:stock", "sale:1:user:10", "stock-restore:processed:event-123")
        given(redisTemplate.execute(eqValue(stockRestoreIdempotentScript), eqValue(keys), anyValue(Array<Any>::class.java))).willReturn(null)

        assertBusinessError(GlobalErrorCode.INTERNAL_SERVER_ERROR) {
            inventoryService.restoreStockIdempotently("event-123", 1L, 10L, 1)
        }
    }

    @Test
    @DisplayName("멱등 재고 복구 시 stock Key가 없으면 재고 정보 누락 예외가 발생한다")
    fun restoreStockIdempotentlyWithoutStockKey() {
        val keys = listOf("sale:1:stock", "sale:1:user:10", "stock-restore:processed:event-123")
        given(redisTemplate.execute(eqValue(stockRestoreIdempotentScript), eqValue(keys), anyValue(Array<Any>::class.java))).willReturn(-1L)

        assertBusinessError(SaleErrorCode.REDIS_STOCK_NOT_FOUND) {
            inventoryService.restoreStockIdempotently("event-123", 1L, 10L, 1)
        }
    }

    private fun assertBusinessError(
        expectedErrorCode: ErrorCode,
        action: () -> Unit,
    ) {
        assertThatThrownBy(action)
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ exception: Throwable ->
                assertThat((exception as BusinessException).errorCode).isEqualTo(expectedErrorCode)
            })
    }

    @Suppress("UNCHECKED_CAST")
    private fun objectArrayCaptor(): ArgumentCaptor<Array<Any>> =
        ArgumentCaptor.forClass(Array<Any>::class.java) as ArgumentCaptor<Array<Any>>

    companion object {
        @JvmStatic
        fun decrementErrorCodes(): Stream<Arguments> =
            Stream.of(
                Arguments.of(-1L, SaleErrorCode.NOT_ENOUGH_STOCK),
                Arguments.of(-2L, SaleErrorCode.INVALID_SALE_PERIOD),
                Arguments.of(-3L, SaleErrorCode.EXCEEDED_PURCHASE_QUANTITY),
                Arguments.of(-4L, SaleErrorCode.SALE_CLOSED),
            )
    }
}
