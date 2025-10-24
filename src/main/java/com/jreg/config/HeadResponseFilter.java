package com.jreg.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Filter to preserve Content-Length headers in HEAD responses for Spring Framework 6.2+.
 * 
 * Spring Framework 6.2 changed MockMvc's handling of HEAD responses to be more strict,
 * often stripping Content-Length headers. This filter captures headers set by controllers
 * and ensures they're preserved in the final response.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HeadResponseFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        if ("HEAD".equalsIgnoreCase(httpRequest.getMethod())) {
            // Wrap the response to capture headers
            HeadResponseWrapper wrapper = new HeadResponseWrapper(httpResponse);
            chain.doFilter(request, wrapper);
            
            // Ensure captured headers are set on the actual response
            wrapper.copyHeadersToResponse();
        } else {
            chain.doFilter(request, response);
        }
    }

    /**
     * Response wrapper that captures headers set during HEAD request processing
     */
    private static class HeadResponseWrapper extends HttpServletResponseWrapper {
        private final HttpServletResponse originalResponse;
        private final Map<String, String> capturedHeaders = new HashMap<>();

        public HeadResponseWrapper(HttpServletResponse response) {
            super(response);
            this.originalResponse = response;
        }

        @Override
        public void setHeader(String name, String value) {
            capturedHeaders.put(name, value);
            super.setHeader(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            capturedHeaders.put(name, value);
            super.addHeader(name, value);
        }

        public void copyHeadersToResponse() {
            // Re-apply all captured headers to ensure they're not stripped
            for (Map.Entry<String, String> entry : capturedHeaders.entrySet()) {
                if (!originalResponse.containsHeader(entry.getKey())) {
                    originalResponse.setHeader(entry.getKey(), entry.getValue());
                }
            }
        }
    }
}
