package com.team3.gudit.cs.client;

import com.team3.gudit.cs.dto.CsWorkflowRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CsWorkflowClient {

    private final RestClient restClient;
    private final String webhookUrl;

    public CsWorkflowClient(
            @Value("${cs.workflow.webhook-url}") String webhookUrl
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(5000);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();

        this.webhookUrl = webhookUrl;
    }

    public void sendInquiry(
            String orderId,
            String message
    ) {
        CsWorkflowRequest request =
                new CsWorkflowRequest(
                        orderId,
                        message
                );

        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}