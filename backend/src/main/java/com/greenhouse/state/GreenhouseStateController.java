package com.greenhouse.state;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/state")
public class GreenhouseStateController {

    private final GreenhouseStateService greenhouseStateService;

    public GreenhouseStateController(GreenhouseStateService greenhouseStateService) {
        this.greenhouseStateService = greenhouseStateService;
    }

    @GetMapping
    public GreenhouseStateResponse getCurrentState() {
        return greenhouseStateService.getCurrentState();
    }
}
