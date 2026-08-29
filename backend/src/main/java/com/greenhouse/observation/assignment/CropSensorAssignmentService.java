package com.greenhouse.observation.assignment;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CropSensorAssignmentService {

    private final CropSensorAssignmentRepository assignmentRepository;

    public CropSensorAssignmentService(CropSensorAssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }

    // Empty means genuinely "no probe serves this crop" - which is a reportable
    // state (NO_SENSOR_ASSIGNED), not a reason to infer anything about soil.
    public Optional<CropSensorAssignment> findCurrentAssignmentForCrop(Long cropId) {
        return assignmentRepository.findByCropIdAndValidToIsNull(cropId);
    }

    public Optional<CropSensorAssignment> findCurrentAssignmentForSensor(String sensorId) {
        return assignmentRepository.findBySensorIdAndValidToIsNull(sensorId);
    }

    public List<CropSensorAssignment> listCurrentAssignments() {
        return assignmentRepository.findAllByValidToIsNull();
    }

    public Map<Long, CropSensorAssignment> currentAssignmentsByCropId() {
        return assignmentRepository.findAllByValidToIsNull().stream()
                .collect(Collectors.toMap(CropSensorAssignment::getCropId, Function.identity()));
    }
}
