package com.team3.gudit.cs.controller;

import com.team3.gudit.auth.security.CustomUserDetails;
import com.team3.gudit.cs.dto.CsInquiryRequest;
import com.team3.gudit.cs.service.CsInquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class CsInquiryController {

    private final CsInquiryService csInquiryService;

    @PostMapping("/{purchaseId}/cs-inquiries")
    public ResponseEntity<Void> submitInquiry(
            @PathVariable Long purchaseId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CsInquiryRequest request
    ) {
        csInquiryService.submitInquiry(
                userDetails.getUserId(),
                purchaseId,
                request.message()
        );

        return ResponseEntity.ok().build();
    }
}