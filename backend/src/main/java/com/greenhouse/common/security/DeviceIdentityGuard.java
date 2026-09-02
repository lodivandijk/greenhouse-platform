package com.greenhouse.common.security;

import com.greenhouse.common.DomainValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// A device credential buys the right to report as THAT device, not as any
// device.
//
// Without this, one probe's token - and a probe sits in a greenhouse where
// anyone can reach it - would be enough to forge readings for every other
// device, which is exactly the kind of false fact the assessment engine is
// built to trust (ADR-025).
@Component
public class DeviceIdentityGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceIdentityGuard.class);

    public void verify(HttpServletRequest request, String claimedDeviceId) {
        Object authenticated = request.getAttribute(
                ApiAuthenticationFilter.AUTHENTICATED_DEVICE_ATTRIBUTE);

        if (authenticated == null) {
            // Unauthenticated telemetry only reaches here during the migration
            // window; the filter has already warned about it.
            return;
        }

        if (!authenticated.equals(claimedDeviceId)) {
            LOGGER.warn(
                    "Rejected telemetry: device {} attempted to report as {}",
                    authenticated, claimedDeviceId
            );
            throw new DomainValidationException(
                    "This device credential may only report telemetry for device '" + authenticated + "'.");
        }
    }
}
