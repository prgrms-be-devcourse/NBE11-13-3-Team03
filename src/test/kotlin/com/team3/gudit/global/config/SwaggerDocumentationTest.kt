package com.team3.gudit.global.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import org.springframework.test.context.bean.override.mockito.MockitoBean
import com.team3.gudit.outbox.consumer.PaymentCompensationConsumerGroupInitializer
import com.team3.gudit.outbox.consumer.StockRestoreConsumerGroupInitializer
import com.team3.gudit.outbox.consumer.PaymentCompensationConsumer
import com.team3.gudit.outbox.consumer.StockRestoreConsumer

@SpringBootTest
@AutoConfigureMockMvc
@MockitoBean(types = [PaymentCompensationConsumerGroupInitializer::class, StockRestoreConsumerGroupInitializer::class,
    PaymentCompensationConsumer::class, StockRestoreConsumer::class])
class SwaggerDocumentationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) {
    @Test
    @WithMockUser(authorities = ["ADMIN"])
    fun `생성된 문서의 인증 정의와 최신 API 설명이 일치한다`() {
        val response = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk()).andReturn().response.contentAsString
        val document = objectMapper.readTree(response)
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/swagger-audit.json"), response)
        val schemes = document.path("components").path("securitySchemes")
        val paths = document.path("paths")
        assertEquals("access_token", schemes.path("cookieAuth").path("name").asText())
        assertEquals("X-INTERNAL-KEY", schemes.path("internalApiKey").path("name").asText())
        for (path in paths) {
            for (operation in path) {
                for (requirement in operation.path("security")) {
                    for (name in requirement.propertyNames()) assertTrue(schemes.has(name), "Undefined security scheme: $name")
                }
            }
        }
        for (path in listOf("/api/users/me", "/api/purchases", "/api/goods", "/api/goods/{goodsId}")) {
            assertTrue(paths.path(path).path("get").path("security").toString().contains("cookieAuth"), path)
        }
        val internal = paths.path("/api/internal/payments/{orderId}/cs-status").path("get")
        assertTrue(internal.path("security").toString().contains("internalApiKey"))
        assertTrue(internal.path("responses").has("401"))
        val inquiry = paths.path("/api/purchases/{purchaseId}/cs-inquiries").path("post")
        assertEquals("구매 문의 접수", inquiry.path("summary").asText())
        assertTrue(inquiry.path("security").toString().contains("cookieAuth"))
        assertTrue(inquiry.path("responses").has("400"))
        assertTrue(paths.path("/api/purchases/{purchaseId}/cancel").path("post").path("responses").path("409").path("description").asText().contains("PURCHASE_005"))
        assertEquals("#/components/schemas/ErrorResponse", paths.path("/api/users/me").path("get").path("responses").path("401").path("content").path("application/json").path("schema").path("\$ref").asText())
        assertEquals("yyyy-MM-dd HH:mm:ss", document.path("components").path("schemas").path("SaleCreateRequestDto").path("properties").path("startAt").path("format").asText())
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
    }
}
