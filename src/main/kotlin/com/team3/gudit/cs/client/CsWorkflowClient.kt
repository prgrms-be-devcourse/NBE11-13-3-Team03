package com.team3.gudit.cs.client

import com.team3.gudit.cs.dto.CsWorkflowRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class CsWorkflowClient(
    @Value("\${cs.workflow.webhook-url}") webhookUrl: String
) {
    private val restClient: RestClient
    private val webhookUrl: String = webhookUrl

    init {
        val requestFactory = SimpleClientHttpRequestFactory()
        requestFactory.setConnectTimeout(3000)
        requestFactory.setReadTimeout(5000)

        restClient = RestClient.builder()
            .requestFactory(requestFactory)
            .build()
    }

    fun sendInquiry(orderId: String?, message: String?) {
        val request = CsWorkflowRequest(orderId, message)

        restClient.post()
            .uri(webhookUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toBodilessEntity()
    }
}
