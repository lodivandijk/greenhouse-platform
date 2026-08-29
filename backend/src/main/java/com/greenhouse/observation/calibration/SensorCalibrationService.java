package com.greenhouse.observation.calibration;

import com.greenhouse.common.DomainValidationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Service
public class SensorCalibrationService {

    private final SensorCalibrationRepository calibrationRepository;
    private final Clock clock;

    public SensorCalibrationService(SensorCalibrationRepository calibrationRepository, Clock clock) {
        this.calibrationRepository = calibrationRepository;
        this.clock = clock;
    }

    public Optional<SensorCalibration> findCurrentCalibration(String sensorId) {
        return calibrationRepository.findBySensorIdAndValidToIsNull(sensorId);
    }

    public List<SensorCalibration> listCurrentCalibrations() {
        return calibrationRepository.findAllByValidToIsNull();
    }

    // index = 100 * (raw - dry) / (wet - dry), clamped to 0..100.
    //
    // Written this way rather than assuming "higher raw = drier" so it works
    // for probes wired the other way round: if wet > dry the arithmetic simply
    // runs in the opposite direction and still yields 0 at the dry reference
    // and 100 at the wet one.
    public MoistureIndex calculateIndex(SensorCalibration calibration, int rawAdc) {
        int dry = calibration.getDryReferenceRaw();
        int wet = calibration.getWetReferenceRaw();

        if (dry == wet) {
            // Guarded by a database CHECK too; reaching here means a
            // calibration was constructed in memory rather than loaded.
            throw new DomainValidationException(
                    "Calibration for sensor " + calibration.getSensorId()
                            + " has identical dry and wet references and cannot produce an index.");
        }

        double raw = 100.0 * (rawAdc - dry) / (double) (wet - dry);
        double clamped = Math.max(0.0, Math.min(100.0, raw));

        return new MoistureIndex(clamped, rawAdc, calibration.getId(), calibration.getVersion());
    }

    // Recalibration appends a new version and retires the previous one rather
    // than editing it, so historical assessments still resolve the calibration
    // that actually produced them (ADR-022).
    public SensorCalibration recalibrate(
            String sensorId,
            int dryReferenceRaw,
            int wetReferenceRaw,
            String calibratedBy,
            String method,
            String notes
    ) {
        if (sensorId == null || sensorId.isBlank()) {
            throw new DomainValidationException("sensorId is required.");
        }
        if (dryReferenceRaw == wetReferenceRaw) {
            throw new DomainValidationException(
                    "dryReferenceRaw and wetReferenceRaw must differ - identical references cannot produce an index.");
        }
        if (calibratedBy == null || calibratedBy.isBlank()) {
            throw new DomainValidationException("calibratedBy is required.");
        }

        Optional<SensorCalibration> previous = calibrationRepository.findBySensorIdAndValidToIsNull(sensorId);

        SensorCalibration calibration = new SensorCalibration();
        calibration.setSensorId(sensorId);
        calibration.setVersion(previous.map(p -> p.getVersion() + 1).orElse(1));
        calibration.setDryReferenceRaw(dryReferenceRaw);
        calibration.setWetReferenceRaw(wetReferenceRaw);
        calibration.setCalibratedAt(clock.instant());
        calibration.setCalibratedBy(calibratedBy);
        calibration.setMethod(method);
        calibration.setNotes(notes);
        calibration.setSupersedesCalibrationId(previous.map(SensorCalibration::getId).orElse(null));

        previous.ifPresent(p -> {
            p.setValidTo(clock.instant());
            calibrationRepository.save(p);
        });

        return calibrationRepository.save(calibration);
    }
}
