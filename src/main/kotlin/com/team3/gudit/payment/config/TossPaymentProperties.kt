package com.team3.gudit.payment.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "toss.payments")
class TossPaymentProperties {
    var clientKey: String? = null
    var secretKey: String? = null
}
