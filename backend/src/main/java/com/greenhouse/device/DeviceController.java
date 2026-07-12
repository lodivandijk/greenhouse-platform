package com.greenhouse.device;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceRegistry deviceRegistry;

    public DeviceController(DeviceRegistry deviceRegistry) {
        this.deviceRegistry = deviceRegistry;
    }

    @GetMapping
    public List<DeviceStatus> getAllDevices() {
        return deviceRegistry.getAllDevices();
    }

    @GetMapping("/{deviceId}")
    public DeviceStatus getDevice(@PathVariable String deviceId) {
        return deviceRegistry.getDevice(deviceId);
    }
}
