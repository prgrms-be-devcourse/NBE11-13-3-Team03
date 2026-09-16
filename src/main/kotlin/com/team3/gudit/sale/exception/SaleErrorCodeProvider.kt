package com.team3.gudit.sale.exception

import com.team3.gudit.global.exception.ErrorCode
import com.team3.gudit.global.exception.ErrorCodeProvider
import org.springframework.stereotype.Component

@Component
class SaleErrorCodeProvider : ErrorCodeProvider<SaleErrorCode> {
    override fun getDomain(): String = "SALE"

    override fun getErrorCodes(): List<ErrorCode> = SaleErrorCode.entries.toList()
}
