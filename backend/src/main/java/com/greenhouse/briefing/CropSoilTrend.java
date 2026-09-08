package com.greenhouse.briefing;

// How a crop's soil has moved over the trend window, and - separately, and
// clearly labelled - what that rate would imply if it simply continued.
//
// The mint wilted after six days of steady decline that a current-state-only
// briefing could not show: every individual day looked survivable. The trend is
// the thing that was missing (ADR-029).
public record CropSoilTrend(
        Direction direction,
        // Index points per day. Negative means drying.
        double changePerDay,
        int daysObserved,
        Double earliestIndex,
        Double latestIndex,
        // Null unless the crop is drying towards a threshold it has not yet
        // reached. An EXTRAPOLATION, not a forecast - see projectionCaveat().
        Double daysUntilDryThreshold,
        // A day-over-day jump large enough to be watering rather than weather.
        boolean sharpRiseObserved
) {

    public enum Direction {
        RISING,
        FALLING,
        STEADY,
        // Not enough history to say anything - a new probe, or a gap in data.
        UNKNOWN
    }

    public static CropSoilTrend unknown() {
        return new CropSoilTrend(Direction.UNKNOWN, 0.0, 0, null, null, null, false);
    }

    public boolean isKnown() {
        return direction != Direction.UNKNOWN;
    }

    public static String projectionCaveat() {
        return "Projections assume the current rate simply continues, which it will not: soil dries more "
                + "slowly as it gets drier, weather changes, and watering resets it. Treat it as "
                + "\"sooner than you think\" rather than a date.";
    }
}
