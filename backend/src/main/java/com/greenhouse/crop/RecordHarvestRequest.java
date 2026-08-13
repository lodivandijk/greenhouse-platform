package com.greenhouse.crop;

import java.time.Instant;

public record RecordHarvestRequest(
        Instant harvestedAt,
        Double quantity,
        HarvestUnit unit,
        String notes
) {
}
