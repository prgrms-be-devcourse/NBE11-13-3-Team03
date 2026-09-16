package com.team3.gudit.cs.controller;

import com.team3.gudit.auth.filter.InternalApiKeyFilter;
import com.team3.gudit.auth.jwt.TokenProvider;
import com.team3.gudit.payment.dto.PaymentStatusResult;
import com.team3.gudit.payment.entity.PaymentStatus;
import com.team3.gudit.payment.service.PaymentService;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalCsController.class)
@Import(InternalApiKeyFilter.class)
class InternalCsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private TokenProvider tokenProvider;

    @Test
    @DisplayName("내부 결제 상태 조회 API를 명세 경로로 호출할 수 있다")
    void getCsStatus() throws Exception {
        // given
        String orderId = "GUDIT_test-order-id";

        given(paymentService.getStatus(orderId))
                .willReturn(
                        new PaymentStatusResult(
                                orderId,
                                100L,
                                PurchaseStatus.PURCHASED,
                                PaymentStatus.DONE,
                                15000
                        )
                );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/internal/payments/{orderId}/cs-status",
                                orderId
                        )
                                .with(user("test"))
                                .header(
                                        "X-INTERNAL-KEY",
                                        "test-internal-api-key"
                                )
                )
                .andExpect(status().isOk());
    }
}