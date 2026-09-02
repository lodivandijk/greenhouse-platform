package com.greenhouse.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;
import java.util.Optional;

// Credentials for the REST surface. Every value is environment-backed; nothing
// is committed (ADR-025).
//
// Devices get their OWN credentials, one per device, rather than sharing the
// admin token: a probe in a greenhouse is physically accessible and its token
// should buy nothing except the right to report its own readings.
@Validated
@ConfigurationProperties(prefix = "greenhouse.security")
public record ApiSecurityProperties(
        String adminToken,
        // deviceId -> token
        Map<String, String> deviceTokens,
        // Lets device authentication be introduced without cutting off a
        // greenhouse whose firmware has not been updated yet. Warn-only is a
        // MIGRATION state, not a resting state - see ADR-025.
        boolean deviceAuthRequired,
        boolean adminAuthRequired
) {

    public ApiSecurityProperties {
        // An unset environment variable binds as an empty string, not as an
        // absent entry. A blank token is a placeholder, never a credential -
        // treating one as real would authenticate anybody who sent "Bearer ".
        deviceTokens = deviceTokens == null ? Map.of() : deviceTokens.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, Map.Entry::getValue));

        if (adminAuthRequired && (adminToken == null || adminToken.isBlank())) {
            throw new IllegalArgumentException(
                    "greenhouse.security.admin-token is required when admin authentication is enforced. "
                            + "Set GREENHOUSE_ADMIN_TOKEN, or set GREENHOUSE_ADMIN_AUTH_REQUIRED=false only "
                            + "if you genuinely intend an unauthenticated write surface.");
        }
        if (deviceAuthRequired && deviceTokens.isEmpty()) {
            throw new IllegalArgumentException(
                    "greenhouse.security.device-tokens must contain at least one non-blank device token "
                            + "when device authentication is enforced (set GREENHOUSE_DEVICE_TOKEN_ESP32_01), "
                            + "otherwise no device could ever report telemetry.");
        }
    }

    // Which device, if any, this token belongs to. Constant-time compared
    // against every configured token so a wrong token cannot be narrowed down
    // by timing.
    public Optional<String> deviceForToken(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return Optional.empty();
        }
        String matched = null;
        for (Map.Entry<String, String> entry : deviceTokens.entrySet()) {
            if (TokenComparison.constantTimeEquals(entry.getValue(), presentedToken)) {
                matched = entry.getKey();
            }
        }
        return Optional.ofNullable(matched);
    }

    public boolean isAdminToken(String presentedToken) {
        return adminToken != null
                && !adminToken.isBlank()
                && TokenComparison.constantTimeEquals(adminToken, presentedToken);
    }
}
