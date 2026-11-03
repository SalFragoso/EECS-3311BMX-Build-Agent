package com.salvador.bmxbuilder.domain.compatibility;

import com.salvador.bmxbuilder.domain.BikeBuild;
import com.salvador.bmxbuilder.domain.Part;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Runs every compatibility rule on a build and collects the results into one report. */
@Component
public class CompatibilityChecker {

    private final List<CompatibilityRule> rules;

    /** The full rule set. Spring uses this constructor. */
    public CompatibilityChecker() {
        this(List.of(
                new BottomBracketRule(),
                new CrankSpindleRule(),
                new SprocketBoreRule(),
                new HeadTubeRule(),
                new FrontAxleRule(),
                new RearAxleRule(),
                new TireClearanceRule()));
    }

    /** A custom rule set (handy for tests). */
    public CompatibilityChecker(List<CompatibilityRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public CompatibilityReport check(BikeBuild build) {
        List<RuleResult> results = new ArrayList<>();
        for (CompatibilityRule rule : rules) {
            if (rule.appliesTo(build)) {
                results.add(rule.evaluate(build));
            } else {
                results.add(RuleResult.notChecked(rule.name()));
            }
        }
        return new CompatibilityReport(results);
    }

    /** Would this part fit the build? Checks a copy, so the real build isn't changed. */
    public boolean isCompatible(BikeBuild build, Part candidate) {
        return check(build.withPart(candidate)).overall() != Status.FAIL;
    }
}
