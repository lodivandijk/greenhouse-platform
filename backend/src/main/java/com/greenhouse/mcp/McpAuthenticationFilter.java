package com.greenhouse.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// The MCP endpoint must not be reachable anonymously. This is intentionally a plain
// bearer-token check (no Spring Security dependency) rather than a full OAuth setup -
// the MCP milestone spec explicitly scopes authentication to "simple bearer-token" for
// this release. Every other REST endpoint is untouched: this filter only inspects
// requests under /mcp.
@Component
public class McpAuthenticationFilter extends OncePerRequestFilter {

    private static final String MCP_PATH_PREFIX = "/mcp";
    private static final String BEARER_PREFIX = "Bearer ";

    private final String configuredToken;

    public McpAuthenticationFilter(@Value("${greenhouse.mcp.auth-token:}") String configuredToken) {
        this.configuredToken = configuredToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(MCP_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!isAuthorized(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("WWW-Authenticate", "Bearer");
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Missing or invalid bearer token.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthorized(HttpServletRequest request) {
        if (configuredToken == null || configuredToken.isBlank()) {
            // Fail closed: an unconfigured token must never mean "anyone may connect."
            return false;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return false;
        }

        String presentedToken = header.substring(BEARER_PREFIX.length());
        return constantTimeEquals(configuredToken, presentedToken);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
