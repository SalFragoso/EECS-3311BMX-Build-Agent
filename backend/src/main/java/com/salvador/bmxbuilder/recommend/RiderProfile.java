package com.salvador.bmxbuilder.recommend;

import java.math.BigDecimal;
import java.util.List;

/**
 * The F01 quiz answers.
 *
 * @param heightCm    rider height in cm
 * @param experience  BEGINNER, INTERMEDIATE or ADVANCED
 * @param spots       where they ride, e.g. "street", "skatepark", "dirt jumps", "track", "flat ground"
 * @param goals       free text, e.g. "learn to grind rails and do 180s"
 * @param budget      optional budget in CAD
 * @param wantsBrakes whether they want brakes
 */
public record RiderProfile(int heightCm,
                           String experience,
                           List<String> spots,
                           String goals,
                           BigDecimal budget,
                           boolean wantsBrakes) {

    public RiderProfile {
        spots = (spots == null) ? List.of() : List.copyOf(spots);
        goals = (goals == null) ? "" : goals.trim();
        experience = (experience == null || experience.isBlank()) ? "BEGINNER" : experience.trim().toUpperCase();
    }

    /** Throws IllegalArgumentException naming the bad field (F01: inline error messages). */
    public void validate() {
        if (heightCm < 100 || heightCm > 230) {
            throw new IllegalArgumentException("Height should be between 100 and 230 cm");
        }
        if (!List.of("BEGINNER", "INTERMEDIATE", "ADVANCED").contains(experience)) {
            throw new IllegalArgumentException("Experience should be BEGINNER, INTERMEDIATE or ADVANCED");
        }
        if (budget != null && budget.signum() < 0) {
            throw new IllegalArgumentException("Budget can't be negative");
        }
        if (spots.isEmpty() && goals.isBlank()) {
            throw new IllegalArgumentException("Tell us where you ride or what you want to learn");
        }
    }
}
