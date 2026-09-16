package com.team3.gudit.global.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(exception: BusinessException): ResponseEntity<ErrorResponse> {
        val errorCode = exception.errorCode

        log.warn(
            "Business exception: code={}, message={}",
            errorCode.getCode(),
            exception.message,
        )

        return ResponseEntity
            .status(errorCode.getStatus())
            .body(ErrorResponse.from(errorCode))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidException(exception: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fieldErrors = linkedMapOf<String, String>()
        exception.bindingResult.fieldErrors.forEach { error ->
            fieldErrors.putIfAbsent(error.field, error.defaultMessage ?: "잘못된 요청입니다.")
        }

        log.warn("Validation failed: {}", fieldErrors)

        val errorCode = GlobalErrorCode.INVALID_INPUT_VALUE
        val response = ErrorResponse.validation(
            errorCode.getCode(),
            errorCode.getMessage(),
            fieldErrors,
        )

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatchException(
        exception: MethodArgumentTypeMismatchException,
    ): ResponseEntity<ErrorResponse> {
        log.warn(
            "Argument type mismatch. parameter={}, value={}",
            exception.name,
            exception.value,
        )

        val errorCode = GlobalErrorCode.TYPE_MISMATCH
        val response = ErrorResponse.validation(
            errorCode.getCode(),
            errorCode.getMessage(),
            emptyMap(),
        )

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(exception: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled Exception occurred: ", exception)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.internalServerError())
    }
}
