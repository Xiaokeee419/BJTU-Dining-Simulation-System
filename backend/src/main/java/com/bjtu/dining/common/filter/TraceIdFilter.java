package com.bjtu.dining.common.filter;

import com.bjtu.dining.common.util.TraceIdUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incomingTraceId = request.getHeader(TraceIdUtils.TRACE_ID_HEADER);
        String traceId = TraceIdUtils.normalizeTraceId(incomingTraceId);

        MDC.put(TraceIdUtils.TRACE_ID_KEY, traceId);
        response.setHeader(TraceIdUtils.TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceIdUtils.TRACE_ID_KEY);
        }
    }
}
