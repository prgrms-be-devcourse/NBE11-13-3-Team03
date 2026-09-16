package com.team3.gudit.view

import com.team3.gudit.payment.config.TossPaymentProperties
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Hidden
@Controller
class ViewController(
    private val tossPaymentProperties: TossPaymentProperties,
) {
    @GetMapping("/")
    fun home(): String = "index"

    @GetMapping("/sales")
    fun saleList(): String = "sale/list"

    @GetMapping("/sales/{saleId}")
    fun saleDetail(): String = "sale/detail"

    @GetMapping("/mypage/purchases")
    fun purchaseList(): String = "purchase/list"

    @GetMapping("/mypage/purchases/{purchaseId}")
    fun purchaseDetail(): String = "purchase/detail"

    @GetMapping("/admin/goods")
    fun adminGoodsList(): String = "admin/goods-list"

    @GetMapping("/admin/goods/new")
    fun adminGoodsCreate(): String = "admin/goods-form"

    @GetMapping("/admin/goods/{goodsId}/edit")
    fun adminGoodsEdit(): String = "admin/goods-form"

    @GetMapping("/admin/sales")
    fun adminSaleList(): String = "admin/sale-list"

    @GetMapping("/admin/sales/new")
    fun adminSaleCreate(): String = "admin/sale-form"

    @GetMapping("/admin/sales/{saleId}/edit")
    fun adminSaleEdit(): String = "admin/sale-form"

    @GetMapping("/payments")
    fun payment(model: Model): String {
        model.addAttribute("clientKey", tossPaymentProperties.clientKey)
        return "payment/payment"
    }

    @GetMapping("/payments/success")
    fun paymentSuccess(): String = "payment/success"

    @GetMapping("/payments/fail")
    fun paymentFail(): String = "payment/fail"
}
