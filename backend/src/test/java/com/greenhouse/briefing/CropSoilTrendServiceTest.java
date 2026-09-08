package com.greenhouse.briefing;

import com.greenhouse.observation.SoilMoistureReadingRepository;
import com.greenhouse.observation.calibration.MoistureIndex;
import com.greenhouse.observation.calibration.SensorCalibration;
import com.greenhouse.observation.calibration.SensorCalibrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CropSoilTrendServiceTest {

    private static final String SENSOR = "soil-03";
    private static final Instant NOW = Instant.parse("2026-09-08T06:00:00Z");

    @Mock private SoilMoistureReadingRepository readingRepository;
    @Mock private SensorCalibrationService calibrationService;

    private CropSoilTrendService service;

    @BeforeEach
    void setUp() {
        service = new CropSoilTrendService(
                readingRepository, calibrationService, Clock.fixed(NOW, ZoneOffset.UTC));

        SensorCalibration calibration = new SensorCalibration();
        calibration.setId(1L);
        calibration.setSensorId(SENSOR);
        calibration.setVersion(1);
        when(calibrationService.findCurrentCalibration(anyString())).thenReturn(Optional.of(calibration));
        // Raw value IS the index here, so the test reads as the numbers it means.
        when(calibrationService.calculateIndex(any(), anyInt()))
                .thenAnswer(i -> new MoistureIndex((double) (int) i.getArgument(1), i.getArgument(1), 1L, 1));
    }

    private void dailyIndices(double... indices) {
        List<SoilMoistureReadingRepository.DailyRawAverage> rows = new ArrayList<>();
        for (int i = 0; i < indices.length; i++) {
            double value = indices[i];
            Instant day = NOW.minusSeconds((indices.length - 1 - i) * 86400L);
            rows.add(new SoilMoistureReadingRepository.DailyRawAverage() {
                public Instant getDay() {
                    return day;
                }

                public Double getAvgRaw() {
                    return value;
                }

                public Long getSamples() {
                    return 1440L;
                }
            });
        }
        when(readingRepository.findDailyAverageRaw(anyString(), any())).thenReturn(rows);
    }

    // The mint's actual decline.
    @Test
    void aSteadyDeclineIsReportedAsFallingWithItsRate() {
        dailyIndices(78, 74, 67, 62, 50, 46, 44);

        CropSoilTrend trend = service.trendFor(SENSOR, 30.0, 44.0);

        assertThat(trend.direction()).isEqualTo(CropSoilTrend.Direction.FALLING);
        // (44 - 78) over a 6-day span.
        assertThat(trend.changePerDay()).isCloseTo(-5.67, org.assertj.core.data.Offset.offset(0.05));
        assertThat(trend.daysObserved()).isEqualTo(6);
        assertThat(trend.earliestIndex()).isEqualTo(78.0);
        assertThat(trend.latestIndex()).isEqualTo(44.0);
    }

    // The defect the live briefing exposed: projecting from the daily mean told
    // a crop shown at 62 that it would cross 50 "in under a day".
    @Test
    void theProjectionStartsFromTheCurrentReadingNotTheDailyMean() {
        dailyIndices(67, 65, 62, 58, 55, 53, 51);

        CropSoilTrend fromCurrent = service.trendFor(SENSOR, 50.0, 62.0);

        // (62 - 50) / 2.67 per day is about 4.5 days, not the 0.4 the daily
        // mean of 51 would have given.
        assertThat(fromCurrent.daysUntilDryThreshold()).isGreaterThan(3.0);
        assertThat(fromCurrent.latestIndex()).isEqualTo(51.0);
    }

    @Test
    void aCropAlreadyBelowItsLineGetsNoCountdown() {
        dailyIndices(60, 55, 50, 45, 40);

        assertThat(service.trendFor(SENSOR, 50.0, 40.0).daysUntilDryThreshold()).isNull();
    }

    @Test
    void aWateringSpikeIsDetectedAsASharpRise() {
        dailyIndices(40, 38, 36, 80, 74, 70);

        assertThat(service.trendFor(SENSOR, 30.0, 70.0).sharpRiseObserved()).isTrue();
    }

    @Test
    void noiseWithinToleranceIsSteadyRatherThanATrend() {
        dailyIndices(60, 60.5, 59.8, 60.2, 60.1);

        assertThat(service.trendFor(SENSOR, 30.0, 60.0).direction())
                .isEqualTo(CropSoilTrend.Direction.STEADY);
    }

    // Two days is not a trend, and a thin day is a gap rather than a
    // measurement - both must report UNKNOWN rather than inventing a slope.
    @Test
    void tooLittleHistoryIsUnknown() {
        dailyIndices(60);

        assertThat(service.trendFor(SENSOR, 30.0, 60.0).isKnown()).isFalse();
    }

    @Test
    void anUncalibratedProbeHasNoTrend() {
        when(calibrationService.findCurrentCalibration(anyString())).thenReturn(Optional.empty());

        assertThat(service.trendFor(SENSOR, 30.0, 60.0).isKnown()).isFalse();
    }

    @Test
    void aCropWithNoSensorHasNoTrend() {
        assertThat(service.trendFor(null, 30.0, null).isKnown()).isFalse();
    }
}
