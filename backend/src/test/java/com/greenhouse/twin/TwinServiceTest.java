package com.greenhouse.twin;

import com.greenhouse.observation.ObservationService;
import com.greenhouse.observation.ObservationStatus;
import com.greenhouse.observation.SoilMoistureReadingRepository;
import com.greenhouse.twin.config.TwinProperties;
import com.greenhouse.twin.config.ZoneProperties;
import com.greenhouse.twin.model.GreenhouseTwin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwinServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-25T12:00:00Z");

    @Mock
    private ObservationService observationService;

    @Mock
    private SoilMoistureReadingRepository soilMoistureReadingRepository;

    private final Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private final TwinAssembler assembler = new TwinAssembler();

    private static TwinProperties twoDeviceProperties() {
        return new TwinProperties(
                "greenhouse-01",
                "Home Greenhouse",
                Duration.ofMinutes(2),
                Duration.ofMinutes(5),
                new TwinProperties.EnvironmentalLimits(5.0, 35.0, 25.0, 90.0),
                List.of(new ZoneProperties("zone-main", "Main Greenhouse", List.of("device-1", "device-2")))
        );
    }

    @Test
    void requestsLatestObservationForEveryConfiguredDevice() {
        TwinProperties properties = twoDeviceProperties();

        when(observationService.findLatestForDevice("device-1")).thenReturn(Optional.empty());
        when(observationService.findLatestForDevice("device-2"))
                .thenReturn(Optional.of(new ObservationStatus("device-2", 22.0, 60.0, 1012.0, FIXED_NOW.minusSeconds(10))));

        TwinService service = new TwinService(observationService, soilMoistureReadingRepository, assembler, properties, fixedClock);

        GreenhouseTwin twin = service.getCurrentTwin();

        verify(observationService).findLatestForDevice("device-1");
        verify(observationService).findLatestForDevice("device-2");
        assertThat(twin.zones()).hasSize(1);
        assertThat(twin.zones().get(0).devices()).hasSize(2);
        assertThat(twin.generatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void missingObservationsDoNotCauseFailure() {
        TwinProperties properties = new TwinProperties(
                "greenhouse-01",
                "Home Greenhouse",
                Duration.ofMinutes(2),
                Duration.ofMinutes(5),
                new TwinProperties.EnvironmentalLimits(5.0, 35.0, 25.0, 90.0),
                List.of(new ZoneProperties("zone-main", "Main Greenhouse", List.of("device-1")))
        );

        when(observationService.findLatestForDevice("device-1")).thenReturn(Optional.empty());

        TwinService service = new TwinService(observationService, soilMoistureReadingRepository, assembler, properties, fixedClock);

        GreenhouseTwin twin = service.getCurrentTwin();

        assertThat(twin.status()).isNotNull();
        assertThat(twin.zones().get(0).devices().get(0).lastSeenAt()).isNull();
    }

    @Test
    void usesOneConsistentClockTimeForTheWholeAssembly() {
        TwinProperties properties = twoDeviceProperties();

        when(observationService.findLatestForDevice("device-1"))
                .thenReturn(Optional.of(new ObservationStatus("device-1", 20.0, 50.0, 1000.0, FIXED_NOW.minusSeconds(5))));
        when(observationService.findLatestForDevice("device-2"))
                .thenReturn(Optional.of(new ObservationStatus("device-2", 21.0, 51.0, 1001.0, FIXED_NOW.minusSeconds(15))));

        TwinService service = new TwinService(observationService, soilMoistureReadingRepository, assembler, properties, fixedClock);

        GreenhouseTwin twin = service.getCurrentTwin();

        assertThat(twin.generatedAt()).isEqualTo(FIXED_NOW);
    }
}
