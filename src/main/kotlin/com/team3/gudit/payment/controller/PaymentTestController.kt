package com.team3.gudit.payment.controller

import com.team3.gudit.payment.config.TossPaymentProperties
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Hidden
@Controller
class PaymentTestController(
    private val tossPaymentProperties: TossPaymentProperties
) {
    @GetMapping("/payments/test")
    fun paymentTest(
        @RequestParam orderId: String,
        @RequestParam amount: Int,
        model: Model
    ): String {
        model.addAttribute("clientKey", tossPaymentProperties.clientKey)
        model.addAttribute("orderId", orderId)
        model.addAttribute("amount", amount)
        return "payment/test"
    }

    @GetMapping("/payments/test/success")
    fun paymentSuccess(
        @RequestParam paymentKey: String,
        @RequestParam orderId: String,
        @RequestParam amount: Int,
        model: Model
    ): String {
        model.addAttribute("paymentKey", paymentKey)
        model.addAttribute("orderId", orderId)
        model.addAttribute("amount", amount)
        return "payment/success"
    }

    @GetMapping("/payments/test/fail")
    fun paymentFail(
        @RequestParam code: String,
        @RequestParam message: String,
        model: Model
    ): String {
        model.addAttribute("code", code)
        model.addAttribute("message", message)
        return "payment/fail"
    }
}
