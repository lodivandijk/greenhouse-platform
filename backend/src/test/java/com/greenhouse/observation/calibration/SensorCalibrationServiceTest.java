package com.greenhouse.observation.calibration;

import com.greenhouse.common.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@ExtendWith(MockitoExtension.class)
class SensorCalibrationServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-29T12:00:00Z");

    @Mock
    private SensorCalibrationRepository calibrationRepository;

    private SensorCalibrationService service() {
        return new SensorCalibrationService(calibrationRepository, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    private static SensorCalibration calibration(int dry, int wet) {
        SensorCalibration calibration = new SensorCalibration();
        calibration.setId(1L);
        calibration.setSensorId("soil-01");
        calibration.setVersion(1);
        calibration.setDryReferenceRaw(dry);
        calibration.setWetReferenceRaw(wet);
        return calibration;
    }

    @Test
    void index_worksWhenWetRawIsLowerThanDryRaw() {
        // The real probes behave this way: higher raw ADC means drier.
        SensorCalibration calibration = calibration(2814, 1181);

        assertThat(service().calculateIndex(calibration, 2814).value()).isCloseTo(0.0, within(0.01));
        assertThat(service().calculateIndex(calibration, 1181).value()).isCloseTo(100.0, within(0.01));
        // Midpoint of 2814..1181 is 1997.5
        assertThat(service().calculateIndex(calibration, 1998).value()).isCloseTo(50.0, within(0.5));
    }

    @Test
    void index_worksWhenWetRawIsHigherThanDryRaw() {
        // A probe wired the other way round must still yield 0 at dry and 100
        // at wet, without the formula assuming a direction.
        SensorCalibration calibration = calibration(1000, 3000);

        assertThat(service().calculateIndex(calibration, 1000).value()).isCloseTo(0.0, within(0.01));
        assertThat(service().calculateIndex(calibration, 3000).value()).isCloseTo(100.0, within(0.01));
        assertThat(service().calculateIndex(calibration, 2000).value()).isCloseTo(50.0, within(0.01));
    }

    @Test
    void index_clampsBelowZeroAndAboveOneHundred() {
        SensorCalibration calibration = calibration(2814, 1181);

        // Drier than the dry reference, and wetter than the wet reference.
        assertThat(service().calculateIndex(calibration, 3500).value()).isEqualTo(0.0);
        assertThat(service().calculateIndex(calibration, 500).value()).isEqualTo(100.0);
    }

    @Test
    void index_rejectsDegenerateCalibration() {
        SensorCalibration degenerate = calibration(2000, 2000);

        assertThatThrownBy(() -> service().calculateIndex(degenerate, 1500))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("identical dry and wet references");
    }

    @Test
    void index_carriesTheCalibrationVersionUsed() {
        SensorCalibration calibration = calibration(2814, 1181);
        calibration.setVersion(4);

        MoistureIndex index = service().calculateIndex(calibration, 2000);

        assertThat(index.calibrationId()).isEqualTo(1L);
        assertThat(index.calibrationVersion()).isEqualTo(4);
        assertThat(index.rawAdc()).isEqualTo(2000);
    }

    @Test
    void recalibrate_rejectsEqualReferences() {
        assertThatThrownBy(() -> service().recalibrate("soil-01", 2000, 2000, "lodi", "immersion", null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void recalibrate_rejectsMissingCalibratedBy() {
        assertThatThrownBy(() -> service().recalibrate("soil-01", 2800, 1200, "  ", "immersion", null))
                .isInstanceOf(DomainValidationException.class);
    }
}
