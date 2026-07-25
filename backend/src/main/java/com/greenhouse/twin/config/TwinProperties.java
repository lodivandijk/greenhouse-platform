package com.greenhouse.twin.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "greenhouse.twin")
public record TwinProperties(
        @NotBlank String greenhouseId,
        @NotBlank String greenhouseName,
        Duration currentThreshold,
        Duration offlineThreshold,
        @Valid EnvironmentalLimits environmentalLimits,
        @NotEmpty @Valid List<ZoneProperties> zones
) {

    public TwinProperties {
        // @Positive doesn't support java.time.Duration, so these are enforced here instead.
        if (currentThreshold == null || currentThreshold.isZero() || currentThreshold.isNegative()) {
            throw new IllegalArgumentException(
                    "greenhouse.twin.current-threshold must be positive"
            );
        }
        if (offlineThreshold == null || offlineThreshold.compareTo(currentThreshold) <= 0) {
            throw new IllegalArgumentException(
                    "greenhouse.twin.offline-threshold must be greater than greenhouse.twin.current-threshold"
            );
        }
    }

    public record EnvironmentalLimits(
            double minimumTemperatureCelsius,
            double maximumTemperatureCelsius,
            double minimumHumidityPercent,
            double maximumHumidityPercent
    ) {

        public EnvironmentalLimits {
            if (minimumTemperatureCelsius >= maximumTemperatureCelsius) {
                throw new IllegalArgumentException(
                        "greenhouse.twin.environmental-limits.minimum-temperature-celsius must be below maximum-temperature-celsius"
                );
            }
            if (minimumHumidityPercent >= maximumHumidityPercent) {
                throw new IllegalArgumentException(
                        "greenhouse.twin.environmental-limits.minimum-humidity-percent must be below maximum-humidity-percent"
                );
            }
            if (minimumHumidityPercent < 0 || maximumHumidityPercent > 100) {
                throw new IllegalArgumentException(
                        "greenhouse.twin.environmental-limits humidity limits must be between 0 and 100"
                );
            }
        }
    }
}
