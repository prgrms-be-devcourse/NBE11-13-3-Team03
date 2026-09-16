package com.team3.gudit.purchase.exception

import com.team3.gudit.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class PurchaseErrorCode(private val status: HttpStatus, private val code: String, private val message: String) : ErrorCode {

    PURCHASE_NOT_FOUND(HttpStatus.NOT_FOUND, "PURCHASE_001", "구매 내역을 찾을 수 없습니다."),

    DUPLICATE_PURCHASE(HttpStatus.CONFLICT, "PURCHASE_002", "이미 구매한 판매 상품입니다."),

    PURCHASE_ALREADY_CANCELED(HttpStatus.CONFLICT, "PURCHASE_003", "이미 취소된 구매입니다."),

    INVALID_PURCHASE_STATUS(HttpStatus.CONFLICT, "PURCHASE_004", "현재 구매 상태에서는 요청한 작업을 수행할 수 없습니다."),

    PURCHASE_CANCELLATION_PERIOD_EXPIRED(HttpStatus.CONFLICT, "PURCHASE_005", "구매 취소 가능 기간이 종료되었습니다.");

    override fun getStatus(): HttpStatus = status
    override fun getCode(): String = code
    override fun getMessage(): String = message
}
