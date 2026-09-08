package com.greenhouse.briefing;

import java.util.List;

// Everything the narrator needs to describe one crop, and nothing else.
//
// A plain record so the narrator can be tested against invented situations - a
// crop drying towards its line, a crop with no history - without standing up a
// greenhouse to produce them.
public record CropNarrativeInput(
        Long cropId,
        String species,
        boolean manuallyMonitored,
        Double currentIndex,
        String soilUnavailableReason,
        Double dryThresholdIndex,
        Double wetThresholdIndex,
        CropSoilTrend trend,
        List<String> activeAssessmentDescriptions,
        List<String> recentActionDescriptions,
        Integer daysSinceLastAction,
        int windowDays,
        String nextRequiredAction
) {
}
