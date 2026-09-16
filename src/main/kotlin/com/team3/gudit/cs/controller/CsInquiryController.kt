package com.team3.gudit.cs.controller

import com.team3.gudit.auth.security.CustomUserDetails
import com.team3.gudit.cs.dto.CsInquiryRequest
import com.team3.gudit.cs.service.CsInquiryService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/purchases")
class CsInquiryController(
    private val csInquiryService: CsInquiryService
) {
    @PostMapping("/{purchaseId}/cs-inquiries")
    fun submitInquiry(
        @PathVariable purchaseId: Long?,
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @Valid @RequestBody request: CsInquiryRequest
    ): ResponseEntity<Void> {
        csInquiryService.submitInquiry(
            userDetails.userId,
            purchaseId,
            request.message
        )
        return ResponseEntity.ok().build()
    }
}
