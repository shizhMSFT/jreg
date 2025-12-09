package com.jreg.config;

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
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.UUID;

/**
 * HTTP request/response logging filter for observability.
 * Logs method, path, status, and duration for all requests.
 * Adds request_id to MDC for correlation across logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingFilter extends OncePerRequestFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String REQUEST_ID_MDC_KEY = "request_id";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        // Generate or extract request ID
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        
        // Add request ID to MDC for all subsequent logs
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        
        // Add request ID to response header
        response.setHeader(REQUEST_ID_HEADER, requestId);
        
        // For HEAD requests, don't wrap response to avoid Content-Length issues
        boolean isHeadRequest = "HEAD".equalsIgnoreCase(request.getMethod());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Log incoming request
            logRequest(request);
            
            if (isHeadRequest) {
                // Process HEAD request without wrapping
                filterChain.doFilter(request, response);
            } else {
                // Wrap request/response for potential content inspection
                ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
                ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
                
                // Process the request
                filterChain.doFilter(wrappedRequest, wrappedResponse);
                
                // Copy cached response content to actual response
                wrappedResponse.copyBodyToResponse();
            }
            
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            // Log response
            logResponse(request, response, duration);
            
            // Clear MDC
            MDC.clear();
        }
    }
    
    /**
     * Log incoming HTTP request
     */
    private void logRequest(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String clientIp = getClientIp(request);
        
        if (queryString != null && !queryString.isEmpty()) {
            uri = uri + "?" + queryString;
        }
        
        logger.info("HTTP Request: {} {} from {}", method, uri, clientIp);
    }
    
    /**
     * Log HTTP response with status and duration
     */
    private void logResponse(HttpServletRequest request, 
                            HttpServletResponse response, 
                            long duration) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        int status = response.getStatus();
        
        // Log with appropriate level based on status code
        if (status >= 500) {
            logger.error("HTTP Response: {} {} - {} ({}ms) [SERVER ERROR]", 
                        method, uri, status, duration);
        } else if (status >= 400) {
            logger.warn("HTTP Response: {} {} - {} ({}ms) [CLIENT ERROR]", 
                       method, uri, status, duration);
        } else {
            logger.info("HTTP Response: {} {} - {} ({}ms)", 
                       method, uri, status, duration);
        }
        
        // Warn on slow requests (>200ms threshold)
        if (duration > 200) {
            logger.warn("Slow request detected: {} {} took {}ms", method, uri, duration);
        }
    }
    
    /**
     * Extract client IP address from request, considering proxies
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };
        
        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For can contain multiple IPs, take the first one
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Skip logging for actuator endpoints to reduce noise
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health") || path.startsWith("/actuator/prometheus");
    }
}
