package com.team3.gudit.auth.filter;

import com.team3.gudit.auth.exception.AuthErrorCode;
import com.team3.gudit.global.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String INTERNAL_API_PREFIX = "/api/internal/";
    private static final String INTERNAL_API_KEY_HEADER = "X-INTERNAL-KEY";

    private final ObjectMapper objectMapper;

    @Value("${internal.api-key}")
    private String internalApiKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_API_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestApiKey =
                request.getHeader(INTERNAL_API_KEY_HEADER);

        if (requestApiKey == null
                || !internalApiKey.equals(requestApiKey)) {

            AuthErrorCode errorCode =
                    AuthErrorCode.INVALID_INTERNAL_API_KEY;

            response.setStatus(errorCode.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            objectMapper.writeValue(
                    response.getWriter(),
                    ErrorResponse.from(errorCode)
            );

            return;
        }

        filterChain.doFilter(request, response);
    }
}