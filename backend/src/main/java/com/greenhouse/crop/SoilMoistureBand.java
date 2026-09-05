package com.greenhouse.crop;

// How thirsty a crop is: the band decides the moisture-index thresholds at
// which it is reported as needing water, or as sitting too wet.
//
// A band rather than free numbers because a number chosen per crop, by hand, is
// a number nobody can later justify. The mint wilted at index 43.6 against a
// threshold of 30 that came from a spec table and had never been checked against
// a plant. Bands make the judgement explicit and comparable: two crops in the
// same band are being held to the same standard, and changing that standard
// changes it for all of them at once.
//
// HIGH is the thirstiest - flagged soonest - not "high threshold" in some
// abstract sense.
//
// ADDING A BAND (say VERY_HIGH) is a deliberate change, not a configuration
// tweak: add the constant here with its thresholds, and add a migration
// assigning it to the crops that should have it. Nothing else needs to change -
// the assessment rule reads the resolved numbers, not the band.
public enum SoilMoistureBand {

    // Wants soil kept consistently moist; basil, mint. No wet ceiling: these
    // crops are not harmed by the wet end in the way the Mediterranean herbs
    // are, and a ceiling nobody needs is just a source of false alarms.
    HIGH(50.0, null,
            "Wants consistently moist soil. Flagged as needing water at index 50 or below."),

    // The middle ground, for a crop that is neither a bog plant nor a
    // Mediterranean scrubland herb.
    MEDIUM(40.0, 85.0,
            "Likes moderate, even moisture. Flagged as dry at 40 or below, as too wet at 85 or above."),

    // Drought-tolerant; thyme, sage, oregano, tarragon. These have a wet ceiling
    // because for them sitting wet is the real risk, not drying out.
    LOW(30.0, 75.0,
            "Drought-tolerant and prefers drying out between waterings. Flagged as dry at 30 or below, "
                    + "as too wet at 75 or above.");

    private final double dryThresholdIndex;
    private final Double wetThresholdIndex;
    private final String description;

    SoilMoistureBand(double dryThresholdIndex, Double wetThresholdIndex, String description) {
        this.dryThresholdIndex = dryThresholdIndex;
        this.wetThresholdIndex = wetThresholdIndex;
        this.description = description;
    }

    public double dryThresholdIndex() {
        return dryThresholdIndex;
    }

    // Null means no wet ceiling: this crop is never reported as too wet.
    public Double wetThresholdIndex() {
        return wetThresholdIndex;
    }

    public String description() {
        return description;
    }
}
