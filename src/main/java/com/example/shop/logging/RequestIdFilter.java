package com.example.shop.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

// Gives every request an ID and puts it on every log line written while handling that request.
// Runs first (HIGHEST_PRECEDENCE) so even errors from other filters get the ID.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    // Name used in the log pattern: %X{reqId}
    public static final String MDC_KEY = "reqId";

    // Only accept caller-sent IDs made of letters, digits and dashes, max 64 chars.
    // This stops someone from putting fake log lines into our logs through the header.
    private static final String VALID_REQUEST_ID = "^[A-Za-z0-9-]{1,64}$";

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || !requestId.matches(VALID_REQUEST_ID)) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long startTime = System.currentTimeMillis();
        log.info("--> {} {}", request.getMethod(), request.getRequestURI());
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            log.info("<-- {} {} {} ({} ms)", request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);

            // Threads are reused for other requests, so always clear the ID when we're done
            MDC.remove(MDC_KEY);
        }
    }
}
