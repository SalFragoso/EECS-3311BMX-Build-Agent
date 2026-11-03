package com.salvador.bmxbuilder.domain.compatibility;

import com.salvador.bmxbuilder.domain.BikeBuild;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;

import java.util.List;

/**
 * Base for the common kind of rule: "a spec on part A must equal a spec on part B",
 * e.g. the frame's bbType must equal the bottom bracket's bbType.
 * Subclasses only pass in which slots and spec keys to compare.
 */
public abstract class MatchingSpecRule implements CompatibilityRule {

    private final String name;
    private final PartCategory slotA;
    private final String keyA;
    private final PartCategory slotB;
    private final String keyB;

    protected MatchingSpecRule(String name, PartCategory slotA, String keyA,
                               PartCategory slotB, String keyB) {
        this.name = name;
        this.slotA = slotA;
        this.keyA = keyA;
        this.slotB = slotB;
        this.keyB = keyB;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean appliesTo(BikeBuild build) {
        return build.hasPart(slotA) && build.hasPart(slotB);
    }

    @Override
    public RuleResult evaluate(BikeBuild build) {
        Part a = build.getPart(slotA);
        Part b = build.getPart(slotB);
        String valueA = a.spec(keyA);
        String valueB = b.spec(keyB);
        List<Part> involved = List.of(a, b);

        // Missing data is a warning, not a pass (F05: "unknown" instead of guessing)
        if (valueA == null || valueB == null) {
            Part missing = (valueA == null) ? a : b;
            String key = (valueA == null) ? keyA : keyB;
            return new RuleResult(name, Status.WARNING,
                    "Unknown: " + missing.getName() + " has no '" + key + "' spec", involved);
        }
        if (valueA.equalsIgnoreCase(valueB)) {
            return new RuleResult(name, Status.PASS,
                    label(slotA) + " and " + label(slotB) + " match (" + valueA + ")", involved);
        }
        return new RuleResult(name, Status.FAIL,
                label(slotA) + " needs " + valueA + " but the " + label(slotB) + " is " + valueB,
                involved);
    }

    /** FRONT_HUB -> "front hub" */
    private static String label(PartCategory slot) {
        return slot.name().toLowerCase().replace('_', ' ');
    }
}
