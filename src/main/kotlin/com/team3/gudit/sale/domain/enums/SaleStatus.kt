package com.team3.gudit.sale.domain.enums

enum class SaleStatus(
    val description: String,
) {
    READY("판매 대기"),
    ON_SALE("판매 중"),
    SOLD_OUT("품절"),
    CLOSED("판매 종료"),
    DELETED("판매 상품 제거"),
}
