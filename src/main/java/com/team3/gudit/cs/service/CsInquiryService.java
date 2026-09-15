package com.team3.gudit.cs.service;

import com.team3.gudit.cs.client.CsWorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CsInquiryService {

    private final CsInquiryReader csInquiryReader;
    private final CsWorkflowClient csWorkflowClient;

    public void submitInquiry(
            Long userId,
            Long purchaseId,
            String message
    ) {
        String orderId =
                csInquiryReader.getOrderId(
                        userId,
                        purchaseId
                );

        csWorkflowClient.sendInquiry(
                orderId,
                message
        );
    }
}