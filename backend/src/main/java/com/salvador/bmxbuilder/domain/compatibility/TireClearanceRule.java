package com.salvador.bmxbuilder.domain.compatibility;

import com.salvador.bmxbuilder.domain.BikeBuild;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * The tires can't be wider than what the frame (and fork, if picked) can clear.
 * Tire spec "size" looks like "20x2.4" (width is the number after the x).
 * Frame/fork spec "maxTire" is the widest tire they fit, e.g. "2.4".
 */
public class TireClearanceRule implements CompatibilityRule {

    @Override
    public String name() {
        return "Tire clearance";
    }

    @Override
    public boolean appliesTo(BikeBuild build) {
        return build.hasPart(PartCategory.TIRES)
                && (build.hasPart(PartCategory.FRAME) || build.hasPart(PartCategory.FORK));
    }

    @Override
    public RuleResult evaluate(BikeBuild build) {
        Part tires = build.getPart(PartCategory.TIRES);
        Double width = tireWidth(tires.spec("size"));
        List<Part> involved = new ArrayList<>(List.of(tires));

        if (width == null) {
            return new RuleResult(name(), Status.WARNING,
                    "Unknown: " + tires.getName() + " has no readable 'size' spec", involved);
        }

        Status worst = Status.PASS;
        List<String> notes = new ArrayList<>();
        for (PartCategory slot : List.of(PartCategory.FRAME, PartCategory.FORK)) {
            Part holder = build.getPart(slot);
            if (holder == null) {
                continue;
            }
            involved.add(holder);
            Double max = parse(holder.spec("maxTire"));
            if (max == null) {
                notes.add(holder.getName() + " has no 'maxTire' spec");
                if (worst == Status.PASS) {
                    worst = Status.WARNING;
                }
            } else if (width > max) {
                notes.add(width + "\" tires are too wide for the " + holder.getName()
                        + " (max " + max + "\")");
                worst = Status.FAIL;
            }
        }

        String message = notes.isEmpty()
                ? width + "\" tires clear the frame and fork"
                : String.join("; ", notes);
        return new RuleResult(name(), worst, message, involved);
    }

    /** "20x2.4" -> 2.4, or null if it can't be read. */
    private static Double tireWidth(String size) {
        if (size == null || !size.contains("x")) {
            return null;
        }
        return parse(size.substring(size.indexOf('x') + 1));
    }

    private static Double parse(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
