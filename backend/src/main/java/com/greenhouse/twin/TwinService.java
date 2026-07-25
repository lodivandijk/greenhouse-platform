package com.greenhouse.twin;

import com.greenhouse.observation.ObservationService;
import com.greenhouse.observation.ObservationStatus;
import com.greenhouse.twin.config.TwinProperties;
import com.greenhouse.twin.config.ZoneProperties;
import com.greenhouse.twin.model.GreenhouseTwin;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TwinService {

    private final ObservationService observationService;
    private final TwinAssembler twinAssembler;
    private final TwinProperties twinProperties;
    private final Clock clock;

    public TwinService(
            ObservationService observationService,
            TwinAssembler twinAssembler,
            TwinProperties twinProperties,
            Clock clock
    ) {
        this.observationService = observationService;
        this.twinAssembler = twinAssembler;
        this.twinProperties = twinProperties;
        this.clock = clock;
    }

    public GreenhouseTwin getCurrentTwin() {
        Instant now = clock.instant();

        Set<String> deviceIds = twinProperties.zones().stream()
                .map(ZoneProperties::deviceIds)
                .flatMap(List::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Optional<ObservationStatus>> latestObservations = deviceIds.stream()
                .collect(Collectors.toMap(
                        deviceId -> deviceId,
                        observationService::findLatestForDevice
                ));

        return twinAssembler.assemble(twinProperties, latestObservations, now);
    }
}
