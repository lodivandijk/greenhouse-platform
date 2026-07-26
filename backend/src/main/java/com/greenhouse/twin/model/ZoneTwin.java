package com.greenhouse.twin.model;

import java.util.List;

public record ZoneTwin(
        String zoneId,
        String name,
        EnvironmentState environment,
        DataQuality dataQuality,
        List<DeviceTwin> devices
) {
}
