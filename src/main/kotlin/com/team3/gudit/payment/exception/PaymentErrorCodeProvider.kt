package com.team3.gudit.payment.exception

import com.team3.gudit.global.exception.ErrorCode
import com.team3.gudit.global.exception.ErrorCodeProvider

class PaymentErrorCodeProvider : ErrorCodeProvider<PaymentErrorCode> {
    override fun getDomain(): String = "PAYMENT"

    override fun getErrorCodes(): List<ErrorCode> = PaymentErrorCode.entries
}
