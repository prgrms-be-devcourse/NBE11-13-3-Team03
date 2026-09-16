package com.team3.gudit.payment.exception

class TossPaymentException(
    val code: String?,
    message: String?
) : RuntimeException(message)
