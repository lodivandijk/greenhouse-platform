package com.greenhouse.twin;

import com.greenhouse.twin.model.GreenhouseTwin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/twin")
public class TwinController {

    private final TwinService twinService;

    public TwinController(TwinService twinService) {
        this.twinService = twinService;
    }

    @GetMapping
    public GreenhouseTwin getCurrentTwin() {
        return twinService.getCurrentTwin();
    }
}
