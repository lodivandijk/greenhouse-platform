package com.greenhouse.observation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/soil-moisture-readings")
public class SoilMoistureReadingController {

    private final ObservationService observationService;

    public SoilMoistureReadingController(ObservationService observationService) {
        this.observationService = observationService;
    }

    @GetMapping
    public List<SoilMoistureReadingResponse> listReadings(
            @RequestParam(required = false) String sensorId
    ) {
        return sensorId == null
                ? observationService.getAllSoilMoistureReadings()
                : observationService.getSoilMoistureReadingsForSensor(sensorId);
    }
}
