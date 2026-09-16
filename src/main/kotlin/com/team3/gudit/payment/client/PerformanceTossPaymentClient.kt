package com.team3.gudit.payment.client

import com.team3.gudit.payment.config.TossPaymentProperties
import com.team3.gudit.payment.dto.TossPaymentCancelRequest
import com.team3.gudit.payment.dto.TossPaymentConfirmRequest
import com.team3.gudit.payment.dto.TossPaymentResponse
import java.time.OffsetDateTime
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("performance")
class PerformanceTossPaymentClient(
    properties: TossPaymentProperties
) : TossPaymentClient(properties) {
    override fun confirm(
        request: TossPaymentConfirmRequest,
        idempotencyKey: String
    ): TossPaymentResponse = TossPaymentResponse(
        request.paymentKey,
        request.orderId,
        "DONE",
        request.amount,
        OffsetDateTime.now()
    )

    override fun cancel(
        paymentKey: String?,
        request: TossPaymentCancelRequest,
        idempotencyKey: String
    ): TossPaymentResponse = TossPaymentResponse(
        paymentKey,
        "GUDIT_PERF_PAYMENT_CONFIRM_CANCEL_RACE_0003",
        "CANCELED",
        10_106,
        OffsetDateTime.now()
    )
}
