package com.team3.gudit.payment.client

import com.team3.gudit.payment.config.TossPaymentProperties
import com.team3.gudit.payment.dto.TossPaymentCancelRequest
import com.team3.gudit.payment.dto.TossPaymentConfirmRequest
import com.team3.gudit.payment.dto.TossPaymentErrorResponse
import com.team3.gudit.payment.dto.TossPaymentResponse
import com.team3.gudit.payment.exception.TossPaymentException
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Component
@Profile("!performance")
open class TossPaymentClient(properties: TossPaymentProperties) {
    private val restClient: RestClient

    init {
        val encodedSecretKey = Base64.getEncoder().encodeToString(
            (properties.secretKey + ":").toByteArray(StandardCharsets.UTF_8)
        )
        restClient = RestClient.builder()
            .baseUrl(TOSS_API_URL)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic $encodedSecretKey")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    open fun confirm(
        request: TossPaymentConfirmRequest,
        idempotencyKey: String
    ): TossPaymentResponse {
        try {
            return restClient.post()
                .uri("/v1/payments/confirm")
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
                .retrieve()
                .body(TossPaymentResponse::class.java)
                ?: throw NullPointerException()
        } catch (exception: RestClientResponseException) {
            val error = exception.getResponseBodyAs(TossPaymentErrorResponse::class.java)
                ?: throw exception
            throw TossPaymentException(error.code, error.message)
        } catch (exception: ResourceAccessException) {
            throw TossPaymentException(
                "NETWORK_ERROR",
                "결제 승인 요청 중 네트워크 오류가 발생했습니다."
            )
        }
    }

    open fun getPayment(paymentKey: String?): TossPaymentResponse {
        try {
            return restClient.get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .retrieve()
                .body(TossPaymentResponse::class.java)
                ?: throw NullPointerException()
        } catch (exception: RestClientResponseException) {
            val error = exception.getResponseBodyAs(TossPaymentErrorResponse::class.java)
                ?: throw exception
            throw TossPaymentException(error.code, error.message)
        } catch (exception: ResourceAccessException) {
            throw TossPaymentException(
                "NETWORK_ERROR",
                "결제 조회 중 네트워크 오류가 발생했습니다."
            )
        }
    }

    open fun cancel(
        paymentKey: String?,
        request: TossPaymentCancelRequest,
        idempotencyKey: String
    ): TossPaymentResponse = restClient.post()
        .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
        .header("Idempotency-Key", idempotencyKey)
        .body(request)
        .retrieve()
        .body(TossPaymentResponse::class.java)
        ?: throw NullPointerException()

    private companion object {
        const val TOSS_API_URL = "https://api.tosspayments.com"
    }
}
