package com.greenhouse.briefing;

import com.greenhouse.observation.SoilMoistureReadingRepository;
import com.greenhouse.observation.calibration.SensorCalibration;
import com.greenhouse.observation.calibration.SensorCalibrationService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Turns a week of probe readings into the shape of the curve.
//
// This is derived FACT - what the numbers did - not interpretation. It raises
// no assessments and opens no care loops; it exists so a human reading the
// briefing can see a slow decline that no single day's value would reveal
// (ADR-029).
@Service
public class CropSoilTrendService {

    // A week: long enough to show a real drift, short enough that a watering
    // three weeks ago does not flatten the picture.
    private static final Duration TREND_WINDOW = Duration.ofDays(7);

    // Below this, day-to-day movement is probe noise and weather, not a trend
    // worth telling someone about.
    private static final double STEADY_THRESHOLD_PER_DAY = 1.0;

    // A day-over-day jump this large is not evaporation reversing; something
    // added water.
    private static final double SHARP_RISE_INDEX_POINTS = 15.0;

    // Beyond this horizon the extrapolation is meaningless, so it is not shown.
    private static final double MAX_PROJECTION_DAYS = 21.0;

    // A day with too few samples is a data gap, not a measurement.
    private static final long MIN_SAMPLES_PER_DAY = 60;

    private final SoilMoistureReadingRepository readingRepository;
    private final SensorCalibrationService calibrationService;
    private final Clock clock;

    public CropSoilTrendService(
            SoilMoistureReadingRepository readingRepository,
            SensorCalibrationService calibrationService,
            Clock clock
    ) {
        this.readingRepository = readingRepository;
        this.calibrationService = calibrationService;
        this.clock = clock;
    }

    public CropSoilTrend trendFor(String sensorId, Double dryThresholdIndex) {
        if (sensorId == null) {
            return CropSoilTrend.unknown();
        }

        Optional<SensorCalibration> calibration = calibrationService.findCurrentCalibration(sensorId);
        if (calibration.isEmpty()) {
            // Raw ADC alone cannot be compared across probes or turned into a
            // slope anyone can reason about.
            return CropSoilTrend.unknown();
        }

        List<SoilMoistureReadingRepository.DailyRawAverage> daily =
                readingRepository.findDailyAverageRaw(sensorId, clock.instant().minus(TREND_WINDOW));

        List<Double> dailyIndex = new ArrayList<>();
        for (SoilMoistureReadingRepository.DailyRawAverage day : daily) {
            if (day.getSamples() == null || day.getSamples() < MIN_SAMPLES_PER_DAY) {
                continue;
            }
            dailyIndex.add(calibrationService
                    .calculateIndex(calibration.get(), (int) Math.round(day.getAvgRaw()))
                    .value());
        }

        // Two points is the minimum that can describe a direction at all.
        if (dailyIndex.size() < 2) {
            return CropSoilTrend.unknown();
        }

        double earliest = dailyIndex.get(0);
        double latest = dailyIndex.get(dailyIndex.size() - 1);
        int spanDays = dailyIndex.size() - 1;
        double changePerDay = (latest - earliest) / spanDays;

        boolean sharpRise = false;
        for (int i = 1; i < dailyIndex.size(); i++) {
            if (dailyIndex.get(i) - dailyIndex.get(i - 1) >= SHARP_RISE_INDEX_POINTS) {
                sharpRise = true;
                break;
            }
        }

        CropSoilTrend.Direction direction;
        if (Math.abs(changePerDay) < STEADY_THRESHOLD_PER_DAY) {
            direction = CropSoilTrend.Direction.STEADY;
        } else if (changePerDay > 0) {
            direction = CropSoilTrend.Direction.RISING;
        } else {
            direction = CropSoilTrend.Direction.FALLING;
        }

        Double daysUntilDry = projectDaysUntilDry(direction, changePerDay, latest, dryThresholdIndex);

        return new CropSoilTrend(
                direction, changePerDay, dailyIndex.size(), earliest, latest, daysUntilDry, sharpRise);
    }

    // Only meaningful while a crop is genuinely drying and has not already
    // crossed the line - once it has, the assessment engine is saying so and a
    // countdown would be noise.
    private Double projectDaysUntilDry(
            CropSoilTrend.Direction direction, double changePerDay, double latest, Double dryThreshold
    ) {
        if (direction != CropSoilTrend.Direction.FALLING || dryThreshold == null) {
            return null;
        }
        if (latest <= dryThreshold) {
            return null;
        }
        double days = (latest - dryThreshold) / Math.abs(changePerDay);
        return days > MAX_PROJECTION_DAYS ? null : days;
    }

    public static Duration trendWindow() {
        return TREND_WINDOW;
    }
}
