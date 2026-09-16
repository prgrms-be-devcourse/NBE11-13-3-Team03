package com.team3.gudit.goods.exception

import com.team3.gudit.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class GoodsErrorCode(
    private val httpStatus: HttpStatus,
    private val errorCode: String,
    private val errorMessage: String,
) : ErrorCode {
    GOODS_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "GOODS_001",
        "해당 상품을 찾을 수 없습니다.",
    );

    override fun getStatus(): HttpStatus = httpStatus

    override fun getCode(): String = errorCode

    override fun getMessage(): String = errorMessage
}
