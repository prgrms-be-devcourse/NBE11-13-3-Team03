package com.team3.gudit.global.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException
import java.util.UUID
import java.util.regex.Pattern

@Component
class TraceIdFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val traceId = resolveTraceId(request)
        val startTime = System.nanoTime()
        var unhandledException = false

        MDC.put(TRACE_ID_MDC_KEY, traceId)
        response.setHeader(TRACE_ID_HEADER, traceId)

        try {
            filterChain.doFilter(request, response)
        } catch (exception: ServletException) {
            unhandledException = true
            throw exception
        } catch (exception: IOException) {
            unhandledException = true
            throw exception
        } catch (exception: RuntimeException) {
            unhandledException = true
            throw exception
        } catch (error: Error) {
            unhandledException = true
            throw error
        } finally {
            val durationMs = (System.nanoTime() - startTime) / 1_000_000

            try {
                writeRequestLog(request, response, durationMs, unhandledException)
            } finally {
                MDC.remove(TRACE_ID_MDC_KEY)
            }
        }
    }

    private fun writeRequestLog(
        request: HttpServletRequest,
        response: HttpServletResponse,
        durationMs: Long,
        unhandledException: Boolean,
    ) {
        val uri = request.requestURI

        if (PROMETHEUS_PATH == uri) {
            return
        }

        val responseStatus = response.status
        val status = if (unhandledException && responseStatus < 500) {
            HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        } else {
            responseStatus
        }
        val method = request.method

        if (status >= 500) {
            log.error(
                "action=HTTP_REQUEST result=FAILED reason=HTTP_{} " +
                    "method={} uri={} status={} durationMs={}",
                status,
                method,
                uri,
                status,
                durationMs,
            )
            return
        }

        if (status >= 400) {
            log.warn(
                "action=HTTP_REQUEST result=REJECTED reason=HTTP_{} " +
                    "method={} uri={} status={} durationMs={}",
                status,
                method,
                uri,
                status,
                durationMs,
            )
            return
        }

        log.info(
            "action=HTTP_REQUEST result=SUCCESS reason=NONE " +
                "method={} uri={} status={} durationMs={}",
            method,
            uri,
            status,
            durationMs,
        )
    }

    private fun resolveTraceId(request: HttpServletRequest): String {
        val receivedTraceId = request.getHeader(TRACE_ID_HEADER)

        if (receivedTraceId != null && VALID_TRACE_ID.matcher(receivedTraceId).matches()) {
            return receivedTraceId
        }

        return UUID.randomUUID().toString().replace("-", "")
    }

    companion object {
        const val TRACE_ID_MDC_KEY = "traceId"
        const val TRACE_ID_HEADER = "X-Trace-Id"

        private const val PROMETHEUS_PATH = "/actuator/prometheus"
        private val VALID_TRACE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$")
    }
}
