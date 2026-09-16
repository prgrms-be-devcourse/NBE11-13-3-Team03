package com.team3.gudit.goods.constant

import java.time.format.DateTimeFormatter

class DateformatConstant private constructor() {
    companion object {
        const val DATE_FORMAT: String = "yyyy-MM-dd HH:mm:ss"

        @JvmField
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT)
    }
}
