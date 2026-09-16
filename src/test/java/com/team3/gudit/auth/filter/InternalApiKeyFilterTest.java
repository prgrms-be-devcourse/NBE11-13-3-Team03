package com.team3.gudit.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class InternalApiKeyFilterTest {

    private static final String INTERNAL_API_KEY =
            "test-internal-api-key";

    private ObjectMapper objectMapper;
    private InternalApiKeyFilter filter;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        objectMapper = mock(ObjectMapper.class);

        filter = new InternalApiKeyFilter(objectMapper);

        ReflectionTestUtils.setField(
                filter,
                "internalApiKey",
                INTERNAL_API_KEY
        );

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("내부 API 요청에 API Key가 없으면 401을 반환한다")
    void internalApiKeyNotFound() throws Exception {
        // given
        given(request.getServletPath())
                .willReturn(
                        "/api/internal/payments/GUDIT_test/cs-status"
                );

        given(request.getHeader("X-INTERNAL-KEY"))
                .willReturn(null);

        StringWriter stringWriter = new StringWriter();

        given(response.getWriter())
                .willReturn(new PrintWriter(stringWriter));

        // when
        filter.doFilterInternal(
                request,
                response,
                filterChain
        );

        // then
        verify(response)
                .setStatus(
                        HttpServletResponse.SC_UNAUTHORIZED
                );

        verify(response)
                .setContentType(
                        MediaType.APPLICATION_JSON_VALUE
                );

        verify(filterChain, never())
                .doFilter(request, response);
    }

    @Test
    @DisplayName("내부 API 요청의 API Key가 일치하지 않으면 401을 반환한다")
    void invalidInternalApiKey() throws Exception {
        // given
        given(request.getServletPath())
                .willReturn(
                        "/api/internal/payments/GUDIT_test/cs-status"
                );

        given(request.getHeader("X-INTERNAL-KEY"))
                .willReturn("invalid-api-key");

        StringWriter stringWriter = new StringWriter();

        given(response.getWriter())
                .willReturn(new PrintWriter(stringWriter));

        // when
        filter.doFilterInternal(
                request,
                response,
                filterChain
        );

        // then
        verify(response)
                .setStatus(
                        HttpServletResponse.SC_UNAUTHORIZED
                );

        verify(filterChain, never())
                .doFilter(request, response);
    }

    @Test
    @DisplayName("내부 API 요청의 API Key가 일치하면 다음 필터로 진행한다")
    void validInternalApiKey() throws Exception {
        // given
        given(request.getServletPath())
                .willReturn(
                        "/api/internal/payments/GUDIT_test/cs-status"
                );

        given(request.getHeader("X-INTERNAL-KEY"))
                .willReturn(INTERNAL_API_KEY);

        // when
        filter.doFilterInternal(
                request,
                response,
                filterChain
        );

        // then
        verify(filterChain)
                .doFilter(request, response);

        verify(response, never())
                .setStatus(
                        HttpServletResponse.SC_UNAUTHORIZED
                );
    }

    @Test
    @DisplayName("일반 API 요청은 내부 API Key 필터 대상에서 제외한다")
    void shouldNotFilterNormalApi() {
        // given
        given(request.getServletPath())
                .willReturn("/api/payments/confirm");

        // when
        boolean result =
                filter.shouldNotFilter(request);

        // then
        assert result;
    }

    @Test
    @DisplayName("Context Path가 있어도 내부 API Key가 없으면 401을 반환한다")
    void internalApiWithContextPathWithoutKey() throws Exception {
        // given
        given(request.getContextPath())
                .willReturn("/gudit");

        given(request.getRequestURI())
                .willReturn(
                        "/gudit/api/internal/payments/GUDIT_test/cs-status"
                );

        given(request.getServletPath())
                .willReturn(
                        "/api/internal/payments/GUDIT_test/cs-status"
                );

        given(request.getHeader("X-INTERNAL-KEY"))
                .willReturn(null);

        StringWriter stringWriter = new StringWriter();

        given(response.getWriter())
                .willReturn(new PrintWriter(stringWriter));

        // when
        filter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        verify(response)
                .setStatus(
                        HttpServletResponse.SC_UNAUTHORIZED
                );

        verify(filterChain, never())
                .doFilter(request, response);
    }

    @Test
    @DisplayName("내부 API Key 설정이 빈 문자열이면 초기화에 실패한다")
    void blankInternalApiKeyConfiguration() {
        // given
        ReflectionTestUtils.setField(
                filter,
                "internalApiKey",
                " "
        );

        // when & then
        assertThrows(
                IllegalStateException.class,
                filter::validateInternalApiKey
        );
    }

    @Test
    @DisplayName("빈 API Key 헤더는 인증에 실패한다")
    void blankInternalApiKeyHeader() throws Exception {
        // given
        given(request.getServletPath())
                .willReturn(
                        "/api/internal/payments/GUDIT_test/cs-status"
                );

        given(request.getHeader("X-INTERNAL-KEY"))
                .willReturn("");

        StringWriter stringWriter =
                new StringWriter();

        given(response.getWriter())
                .willReturn(
                        new PrintWriter(stringWriter)
                );

        // when
        filter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        verify(response)
                .setStatus(
                        HttpServletResponse.SC_UNAUTHORIZED
                );

        verify(filterChain, never())
                .doFilter(request, response);
    }
}