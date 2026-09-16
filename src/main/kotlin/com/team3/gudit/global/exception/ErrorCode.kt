package com.team3.gudit.global.exception

import org.springframework.http.HttpStatus

interface ErrorCode {
    fun getStatus(): HttpStatus
    fun getCode(): String
    fun getMessage(): String
}
