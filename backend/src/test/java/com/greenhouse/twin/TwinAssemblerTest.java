package com.greenhouse.twin;

import com.greenhouse.observation.ObservationStatus;
import com.greenhouse.twin.config.TwinProperties;
import com.greenhouse.twin.config.ZoneProperties;
import com.greenhouse.twin.model.DeviceTwin;
import com.greenhouse.twin.model.GreenhouseTwin;
import com.greenhouse.twin.model.ZoneTwin;
import com.greenhouse.twin.status.DeviceStatus;
import com.greenhouse.twin.status.FreshnessStatus;
import com.greenhouse.twin.status.TwinStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TwinAssemblerTest {

    private static final Duration CURRENT_THRESHOLD = Duration.ofMinutes(2);
    private static final Duration OFFLINE_THRESHOLD = Duration.ofMinutes(5);
    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");
    private static final String DEVICE_ID = "device-1";

    private final TwinAssembler assembler = new TwinAssembler();

    private static TwinProperties.EnvironmentalLimits defaultLimits() {
        return new TwinProperties.EnvironmentalLimits(5.0, 35.0, 25.0, 90.0);
    }

    private static TwinProperties singleZoneProperties(List<String> deviceIds) {
        return new TwinProperties(
                "greenhouse-01",
                "Home Greenhouse",
                CURRENT_THRESHOLD,
                OFFLINE_THRESHOLD,
                defaultLimits(),
                List.of(new ZoneProperties("zone-main", "Main Greenhouse", deviceIds))
        );
    }

    private static TwinProperties singleZoneProperties() {
        return singleZoneProperties(List.of(DEVICE_ID));
    }

    private static ObservationStatus observation(String deviceId, Double temp, Double humidity, Double pressure, Instant receivedAt) {
        return new ObservationStatus(deviceId, temp, humidity, pressure, receivedAt);
    }

    private GreenhouseTwin assembleSingleDevice(Optional<ObservationStatus> observation) {
        return assembler.assemble(singleZoneProperties(), Map.of(DEVICE_ID, observation), List.of(), NOW);
    }

    private static ZoneTwin firstZone(GreenhouseTwin twin) {
        return twin.zones().get(0);
    }

    private static DeviceTwin firstDevice(GreenhouseTwin twin) {
        return firstZone(twin).devices().get(0);
    }

    @Test
    void noObservation_producesUnknownEverything() {
        GreenhouseTwin twin = assembleSingleDevice(Optional.empty());

        assertThat(firstDevice(twin).status()).isEqualTo(DeviceStatus.UNKNOWN);
        assertThat(firstZone(twin).dataQuality().freshness()).isEqualTo(FreshnessStatus.UNKNOWN);
        assertThat(twin.status()).isEqualTo(TwinStatus.UNKNOWN);
        assertThat(twin.lastUpdatedAt()).isNull();
    }

    @Test
    void currentNormalObservation() {
        Instant observedAt = NOW.minusSeconds(30);
        GreenhouseTwin twin = assembleSingleDevice(
                Optional.of(observation(DEVICE_ID, 22.0, 60.0, 1012.0, observedAt))
        );

        assertThat(firstDevice(twin).status()).isEqualTo(DeviceStatus.ONLINE);
        assertThat(firstZone(twin).dataQuality().freshness()).isEqualTo(FreshnessStatus.CURRENT);
        assertThat(firstZone(twin).dataQuality().complete()).isTrue();
        assertThat(twin.status()).isEqualTo(TwinStatus.NORMAL);
    }

    @Test
    void delayedNormalObservation() {
        Instant observedAt = NOW.minus(Duration.ofMinutes(3));
        GreenhouseTwin twin = assembleSingleDevice(
                Optional.of(observation(DEVICE_ID, 22.0, 60.0, 1012.0, observedAt))
        );

        assertThat(firstDevice(twin).status()).isEqualTo(DeviceStatus.DELAYED);
        assertThat(firstZone(twin).dataQuality().freshness()).isEqualTo(FreshnessStatus.DELAYED);
        assertThat(twin.status()).isEqualTo(TwinStatus.NORMAL);
    }

    @Test
    void offlineObservation() {
        Instant observedAt = NOW.minus(Duration.ofMinutes(5));
        GreenhouseTwin twin = assembleSingleDevice(
                Optional.of(observation(DEVICE_ID, 22.0, 60.0, 1012.0, observedAt))
        );

        assertThat(firstDevice(twin).status()).isEqualTo(DeviceStatus.OFFLINE);
        assertThat(firstZone(twin).dataQuality().freshness()).isEqualTo(FreshnessStatus.STALE);
        assertThat(twin.status()).isEqualTo(TwinStatus.OFFLINE);
    }

    @Test
    void currentThresholdBoundary_isDelayedNotOnline() {
        Instant observedAt = NOW.minus(CURRENT_THRESHOLD);
        GreenhouseTwin twin = assembleSingleDevice(
                Optional.of(observation(DEVICE_ID, 22.0, 60.0, 1012.0, observedAt))
        );

        assertThat(firstDevice(twin).status()).isEqualTo(DeviceStatus.DELAYED);
        assertThat(firstZone(twin).dataQuality().freshness()).isEqualTo(FreshnessStatus.DELAYED);
    }

    @Test
    void offlineThresholdBoundary_isOfflineNotDelayed() {
        Instant observedAt = NOW.minus(OFFLINE_THRESHOLD);
        GreenhouseTwin twin = assembleSingleDevice(
                Optional.of(observation(DEVICE_ID, 22.0, 60.0, 1012.0, observedAt))
        );

        assertThat(firstDevice(twin).status()).isEqualTo(DeviceStatus.OFFLINE);
        assertThat(firstZone(twin).dataQuality().freshness()).isEqualTo(FreshnessStatus.STALE);
    }

    @Test
    void incompleteObservation_isMarkedIncomplete() {
        GreenhouseTwin twin = assembleSingleDevice(
                Optional.of(observation(DEVICE_ID, 22.0, null, 1012.0, NOW.minusSeconds(10)))
        );

        assertThat(firstZone(twin).dataQuality().complete()).isFalse();
    }

    @Test
    void futureObservationTimestamp_ageIsClampedToZero() {
        Instant future = NOW.plusSeconds(30);
        GreenhouseTwin twin = assembleSingleDevice(
                Optional.of(observation(DEVICE_ID, 22.0, 60.0, 1012.0, future))
        );

        assertThat(firstZone(twin).dataQuality().ageSeconds()).isEqualTo(0L);
    }

    @Test
    void multipleDevicesInOneZone_usesNewestObservationForZoneState() {
        String olderDeviceId = "device-old";
        String newerDeviceId = "device-new";
        Instant olderTime = NOW.minusSeconds(90);
        Instant newerTime = NOW.minusSeconds(10);

        TwinProperties properties = singleZoneProperties(List.of(olderDeviceId, newerDeviceId));
        Map<String, Optional<ObservationStatus>> observations = Map.of(
                olderDeviceId, Optional.of(observation(olderDeviceId, 20.0, 50.0, 1000.0, olderTime)),
                newerDeviceId, Optional.of(observation(newerDeviceId, 24.0, 55.0, 1005.0, newerTime))
        );

        GreenhouseTwin twin = assembler.assemble(properties, observations, List.of(), NOW);
        ZoneTwin zone = firstZone(twin);

        assertThat(zone.devices()).extracting(DeviceTwin::deviceId)
                .containsExactlyInAnyOrder(olderDeviceId, newerDeviceId);
        assertThat(zone.environment().temperatureCelsius()).isEqualTo(24.0);
        assertThat(zone.dataQuality().observedAt()).isEqualTo(newerTime);
    }

    @Test
    void multipleZones_preserveOrderAndComputeOverallLastUpdatedAt() {
        Instant zoneAObservedAt = NOW.minusSeconds(60);
        Instant zoneBObservedAt = NOW.minusSeconds(10);

        TwinProperties properties = new TwinProperties(
                "greenhouse-01",
                "Home Greenhouse",
                CURRENT_THRESHOLD,
                OFFLINE_THRESHOLD,
                defaultLimits(),
                List.of(
                        new ZoneProperties("zone-a", "Zone A", List.of("device-a")),
                        new ZoneProperties("zone-b", "Zone B", List.of("device-b"))
                )
        );
        Map<String, Optional<ObservationStatus>> observations = Map.of(
                "device-a", Optional.of(observation("device-a", 20.0, 50.0, 1000.0, zoneAObservedAt)),
                "device-b", Optional.of(observation("device-b", 21.0, 51.0, 1001.0, zoneBObservedAt))
        );

        GreenhouseTwin twin = assembler.assemble(properties, observations, List.of(), NOW);

        assertThat(twin.zones()).extracting(ZoneTwin::zoneId).containsExactly("zone-a", "zone-b");
        assertThat(twin.lastUpdatedAt()).isEqualTo(zoneBObservedAt);
    }

    @Test
    void staleObservation_reportsOffline() {
        Instant staleObservedAt = NOW.minus(OFFLINE_THRESHOLD).minusSeconds(1);
        GreenhouseTwin twin = assembleSingleDevice(
                Optional.of(observation(DEVICE_ID, 38.0, 10.0, 1012.0, staleObservedAt))
        );

        assertThat(firstZone(twin).dataQuality().freshness()).isEqualTo(FreshnessStatus.STALE);
        assertThat(twin.status()).isEqualTo(TwinStatus.OFFLINE);
    }
}
