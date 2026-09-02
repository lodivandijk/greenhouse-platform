package com.greenhouse.heartbeat;

import com.greenhouse.common.security.DeviceIdentityGuard;
import com.greenhouse.device.DeviceService;
import com.greenhouse.device.DeviceStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/heartbeats", "/api/v1/heartbeats"})
public class HeartbeatController {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(HeartbeatController.class);

    private final DeviceService deviceService;
    private final DeviceIdentityGuard deviceIdentityGuard;

    public HeartbeatController(DeviceService deviceService, DeviceIdentityGuard deviceIdentityGuard) {
        this.deviceService = deviceService;
        this.deviceIdentityGuard = deviceIdentityGuard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeviceStatus receiveHeartbeat(
            @Valid @RequestBody HeartbeatRequest request,
            HttpServletRequest httpRequest
    ) {
        deviceIdentityGuard.verify(httpRequest, request.deviceId());

        DeviceStatus status = deviceService.recordHeartbeat(request);

        LOGGER.info(
                "Heartbeat received: deviceId={}, ipAddress={}, signalStrengthDbm={}, count={}",
                status.deviceId(),
                status.ipAddress(),
                status.signalStrengthDbm(),
                status.heartbeatCount()
        );

        return status;
    }
}
