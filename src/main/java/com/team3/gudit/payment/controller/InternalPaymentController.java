package com.team3.gudit.payment.controller;

import com.team3.gudit.payment.dto.PaymentCsStatusResponse;
import com.team3.gudit.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "Internal Payment",
        description = "내부 자동화용 결제 조회 API"
)
@RestController
@RequestMapping("/api/internal/payments")
@RequiredArgsConstructor
public class InternalPaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "CS용 결제 상태 조회",
            description = """
                    주문번호(orderId)를 기준으로 결제 및 구매 상태를 조회합니다.

                    n8n CS Workflow에서 고객 문의와 실제 주문 상태를 함께 분석하기 위한
                    내부 자동화용 조회 API입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "결제 및 구매 상태 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "결제 정보를 찾을 수 없음"
            )
    })
    @GetMapping("/{orderId}/cs-status")
    public ResponseEntity<PaymentCsStatusResponse> getCsStatus(
            @PathVariable String orderId
    ) {
        PaymentCsStatusResponse response =
                paymentService.getCsStatus(orderId);

        return ResponseEntity.ok(response);
    }
}