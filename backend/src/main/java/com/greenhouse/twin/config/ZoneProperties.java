package com.greenhouse.twin.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ZoneProperties(
        @NotBlank String zoneId,
        @NotBlank String name,
        @NotEmpty List<@NotBlank String> deviceIds
) {
}
