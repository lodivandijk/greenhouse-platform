package com.greenhouse.action;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/actions")
public class ActionController {

    private final ActionService actionService;

    public ActionController(ActionService actionService) {
        this.actionService = actionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActionResponse recordAction(@RequestBody RecordActionRequest request) {
        return actionService.recordAction(request.cropId(), request.type(), request.description(),
                request.quantity(), request.unit(), request.performedAt(), request.performedBy());
    }

    @GetMapping
    public List<ActionResponse> listActions(
            @RequestParam(required = false) Long cropId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since
    ) {
        return actionService.listActions(cropId, limit, since);
    }

    @GetMapping("/{actionId}")
    public ActionResponse getAction(@PathVariable Long actionId) {
        return actionService.getAction(actionId);
    }
}
