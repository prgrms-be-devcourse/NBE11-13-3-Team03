package com.team3.gudit.purchase.exception

import com.team3.gudit.global.exception.ErrorCode
import com.team3.gudit.global.exception.ErrorCodeProvider
import org.springframework.stereotype.Component

@Component
class PurchaseErrorCodeProvider : ErrorCodeProvider<PurchaseErrorCode> {
    override fun getDomain(): String = "PURCHASE"
    override fun getErrorCodes(): List<ErrorCode> = PurchaseErrorCode.entries.toList()
}
