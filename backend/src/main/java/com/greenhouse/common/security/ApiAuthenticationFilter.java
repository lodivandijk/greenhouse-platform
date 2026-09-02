package com.greenhouse.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

// Authentication for the REST surface. /mcp is handled separately by
// McpAuthenticationFilter and is skipped here.
//
// Before this existed, the bearer-token check covered /mcp alone, so every
// route that creates, changes or deletes a crop, goal, harvest, observation or
// action was reachable anonymously by anything that could open a socket to port
// 8080. The application binds 0.0.0.0 and the Pi has two LAN interfaces besides
// Tailscale, so "protected by the network" was not true (ADR-025).
//
// Deliberately a plain bearer-token check rather than Spring Security, matching
// the precedent set for MCP: three route classes and two credentials do not
// need a framework, and adding one would be a larger change than the problem.
@Component
public class ApiAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    // Set by this filter once a device credential is recognised, so the
    // ingestion controllers can refuse a device reporting under another
    // device's identity.
    public static final String AUTHENTICATED_DEVICE_ATTRIBUTE = "greenhouse.authenticatedDeviceId";

    private static final String[] DEVICE_INGESTION_PATHS = {
            "/api/v1/heartbeats", "/api/heartbeats",
            "/api/v1/observations", "/api/observations"
    };

    private final ApiSecurityProperties properties;

    public ApiAuthenticationFilter(ApiSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // MCP has its own filter and its own token.
        if (path.startsWith("/mcp")) {
            chain.doFilter(request, response);
            return;
        }

        String presented = presentedToken(request);

        if (isDeviceIngestion(path)) {
            Optional<String> deviceId = properties.deviceForToken(presented);
            if (deviceId.isPresent()) {
                request.setAttribute(AUTHENTICATED_DEVICE_ATTRIBUTE, deviceId.get());
                chain.doFilter(request, response);
                return;
            }
            if (properties.deviceAuthRequired()) {
                unauthorized(response, "Missing or invalid device token.");
                return;
            }
            // Migration window only: the greenhouse keeps reporting while its
            // firmware is updated, but every anonymous reading is announced so
            // this state cannot be forgotten about.
            LOGGER.warn(
                    "UNAUTHENTICATED telemetry accepted on {} from {} - device authentication is not yet "
                            + "enforced. This is a migration state; set greenhouse.security.device-auth-required "
                            + "once the firmware sends a token.",
                    path, request.getRemoteAddr()
            );
            chain.doFilter(request, response);
            return;
        }

        // Reads and the dashboard are deliberately open on the local network;
        // writes are not. See ADR-025 for why that split was chosen rather than
        // authenticating everything.
        if (!isMutation(request)) {
            chain.doFilter(request, response);
            return;
        }

        if (properties.isAdminToken(presented)) {
            chain.doFilter(request, response);
            return;
        }

        if (!properties.adminAuthRequired()) {
            LOGGER.warn(
                    "UNAUTHENTICATED write accepted: {} {} from {} - admin authentication is disabled.",
                    request.getMethod(), path, request.getRemoteAddr()
            );
            chain.doFilter(request, response);
            return;
        }

        LOGGER.warn(
                "Rejected unauthenticated write: {} {} from {}",
                request.getMethod(), path, request.getRemoteAddr()
        );
        unauthorized(response, "Missing or invalid bearer token.");
    }

    private static boolean isDeviceIngestion(String path) {
        for (String ingestionPath : DEVICE_INGESTION_PATHS) {
            if (path.equals(ingestionPath)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMutation(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equals(method)
                || "PUT".equals(method)
                || "PATCH".equals(method)
                || "DELETE".equals(method);
    }

    private static String presentedToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }

    private static void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
