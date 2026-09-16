package com.team3.gudit.auth.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.http.MediaType
import org.springframework.test.util.ReflectionTestUtils
import tools.jackson.databind.ObjectMapper
import java.io.PrintWriter
import java.io.StringWriter

class InternalApiKeyFilterTest {

    private lateinit var objectMapper: ObjectMapper
    private lateinit var filter: InternalApiKeyFilter

    private lateinit var request: HttpServletRequest
    private lateinit var response: HttpServletResponse
    private lateinit var filterChain: FilterChain

    @BeforeEach
    fun setUp() {
        objectMapper = mock(ObjectMapper::class.java)
        filter = InternalApiKeyFilter(objectMapper)

        ReflectionTestUtils.setField(
            filter,
            "internalApiKey",
            INTERNAL_API_KEY,
        )

        request = mock(HttpServletRequest::class.java)
        response = mock(HttpServletResponse::class.java)
        filterChain = mock(FilterChain::class.java)
    }

    @Test
    @DisplayName("내부 API 요청에 API Key가 없으면 401을 반환한다")
    fun internalApiKeyNotFound() {
        given(request.servletPath)
            .willReturn("/api/internal/payments/GUDIT_test/cs-status")
        given(request.getHeader("X-INTERNAL-KEY"))
            .willReturn(null)

        val stringWriter = StringWriter()
        given(response.writer)
            .willReturn(PrintWriter(stringWriter))

        filter.doFilterInternal(request, response, filterChain)

        verify(response).status = HttpServletResponse.SC_UNAUTHORIZED
        verify(response).contentType = MediaType.APPLICATION_JSON_VALUE
        verify(filterChain, never()).doFilter(request, response)
    }

    @Test
    @DisplayName("내부 API 요청의 API Key가 일치하지 않으면 401을 반환한다")
    fun invalidInternalApiKey() {
        given(request.servletPath)
            .willReturn("/api/internal/payments/GUDIT_test/cs-status")
        given(request.getHeader("X-INTERNAL-KEY"))
            .willReturn("invalid-api-key")

        val stringWriter = StringWriter()
        given(response.writer)
            .willReturn(PrintWriter(stringWriter))

        filter.doFilterInternal(request, response, filterChain)

        verify(response).status = HttpServletResponse.SC_UNAUTHORIZED
        verify(filterChain, never()).doFilter(request, response)
    }

    @Test
    @DisplayName("내부 API 요청의 API Key가 일치하면 다음 필터로 진행한다")
    fun validInternalApiKey() {
        given(request.servletPath)
            .willReturn("/api/internal/payments/GUDIT_test/cs-status")
        given(request.getHeader("X-INTERNAL-KEY"))
            .willReturn(INTERNAL_API_KEY)

        filter.doFilterInternal(request, response, filterChain)

        verify(filterChain).doFilter(request, response)
        verify(response, never()).status = HttpServletResponse.SC_UNAUTHORIZED
    }

    @Test
    @DisplayName("일반 API 요청은 내부 API Key 필터 대상에서 제외한다")
    fun shouldNotFilterNormalApi() {
        given(request.servletPath)
            .willReturn("/api/payments/confirm")

        val result = filter.shouldNotFilter(request)

        assert(result)
    }

    @Test
    @DisplayName("Context Path가 있어도 내부 API Key가 없으면 401을 반환한다")
    fun internalApiWithContextPathWithoutKey() {
        given(request.contextPath)
            .willReturn("/gudit")
        given(request.requestURI)
            .willReturn("/gudit/api/internal/payments/GUDIT_test/cs-status")
        given(request.servletPath)
            .willReturn("/api/internal/payments/GUDIT_test/cs-status")
        given(request.getHeader("X-INTERNAL-KEY"))
            .willReturn(null)

        val stringWriter = StringWriter()
        given(response.writer)
            .willReturn(PrintWriter(stringWriter))

        filter.doFilter(request, response, filterChain)

        verify(response).status = HttpServletResponse.SC_UNAUTHORIZED
        verify(filterChain, never()).doFilter(request, response)
    }

    @Test
    @DisplayName("내부 API Key 설정이 빈 문자열이면 초기화에 실패한다")
    fun blankInternalApiKeyConfiguration() {
        ReflectionTestUtils.setField(
            filter,
            "internalApiKey",
            " ",
        )

        assertThrows(IllegalStateException::class.java) {
            filter.validateInternalApiKey()
        }
    }

    @Test
    @DisplayName("빈 API Key 헤더는 인증에 실패한다")
    fun blankInternalApiKeyHeader() {
        given(request.servletPath)
            .willReturn("/api/internal/payments/GUDIT_test/cs-status")
        given(request.getHeader("X-INTERNAL-KEY"))
            .willReturn("")

        val stringWriter = StringWriter()
        given(response.writer)
            .willReturn(PrintWriter(stringWriter))

        filter.doFilter(request, response, filterChain)

        verify(response).status = HttpServletResponse.SC_UNAUTHORIZED
        verify(filterChain, never()).doFilter(request, response)
    }

    private companion object {
        const val INTERNAL_API_KEY = "test-internal-api-key"
    }
}
