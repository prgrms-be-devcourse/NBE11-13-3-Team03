package com.team3.gudit.goods.exception

import com.team3.gudit.global.exception.ErrorCode
import com.team3.gudit.global.exception.ErrorCodeProvider
import org.springframework.stereotype.Component

@Component
class GoodsErrorCodeProvider : ErrorCodeProvider<GoodsErrorCode> {
    override fun getDomain(): String = "GOODS"

    override fun getErrorCodes(): List<ErrorCode> = GoodsErrorCode.entries.toList()
}
