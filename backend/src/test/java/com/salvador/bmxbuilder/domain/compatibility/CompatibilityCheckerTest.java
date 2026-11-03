package com.salvador.bmxbuilder.domain.compatibility;

import com.salvador.bmxbuilder.domain.BikeBuild;
import com.salvador.bmxbuilder.domain.Money;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompatibilityCheckerTest {

    private final CompatibilityChecker checker = new CompatibilityChecker();

    private Part part(PartCategory category, Map<String, String> specs) {
        Part p = new Part("Test", category.name(), category,
                new Money(new BigDecimal("10"), "CAD"), 100, "test.png");
        p.getSpecs().putAll(specs);
        return p;
    }

    private RuleResult resultFor(CompatibilityReport report, String ruleName) {
        return report.results().stream()
                .filter(r -> r.rule().equals(ruleName))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void emptyBuildIsNotCheckedYet() {
        CompatibilityReport report = checker.check(new BikeBuild("Empty"));

        assertEquals(Status.NOT_CHECKED, report.overall());
        assertTrue(report.issues().isEmpty());
    }

    @Test
    void matchingBottomBracketPasses() {
        BikeBuild build = new BikeBuild("Mid");
        build.setPart(PartCategory.FRAME, part(PartCategory.FRAME, Map.of("bbType", "MID")));
        build.setPart(PartCategory.BOTTOM_BRACKET, part(PartCategory.BOTTOM_BRACKET, Map.of("bbType", "mid")));

        assertEquals(Status.PASS, resultFor(checker.check(build), "Bottom bracket shell").status());
    }

    @Test
    void wrongBottomBracketFailsTheWholeBuild() {
        BikeBuild build = new BikeBuild("Mismatch");
        build.setPart(PartCategory.FRAME, part(PartCategory.FRAME, Map.of("bbType", "MID")));
        build.setPart(PartCategory.BOTTOM_BRACKET, part(PartCategory.BOTTOM_BRACKET, Map.of("bbType", "SPANISH")));

        CompatibilityReport report = checker.check(build);

        assertEquals(Status.FAIL, report.overall());
        assertEquals(1, report.issues().size());
        assertTrue(report.issues().get(0).message().contains("SPANISH"));
    }

    @Test
    void missingSpecIsAWarningNotAPass() {
        BikeBuild build = new BikeBuild("No data");
        build.setPart(PartCategory.FRAME, part(PartCategory.FRAME, Map.of()));   // no headTube spec
        build.setPart(PartCategory.FORK, part(PartCategory.FORK, Map.of("steerer", "1-1/8")));

        CompatibilityReport report = checker.check(build);

        assertEquals(Status.WARNING, resultFor(report, "Head tube").status());
        assertEquals(Status.WARNING, report.overall());
    }

    @Test
    void tiresWiderThanTheFrameAllowsFail() {
        BikeBuild build = new BikeBuild("Fat tires");
        build.setPart(PartCategory.FRAME, part(PartCategory.FRAME, Map.of("maxTire", "2.3")));
        build.setPart(PartCategory.TIRES, part(PartCategory.TIRES, Map.of("size", "20x2.4")));

        assertEquals(Status.FAIL, resultFor(checker.check(build), "Tire clearance").status());
    }

    @Test
    void tiresThatFitPass() {
        BikeBuild build = new BikeBuild("Tires ok");
        build.setPart(PartCategory.FRAME, part(PartCategory.FRAME, Map.of("maxTire", "2.4")));
        build.setPart(PartCategory.FORK, part(PartCategory.FORK, Map.of("maxTire", "2.5")));
        build.setPart(PartCategory.TIRES, part(PartCategory.TIRES, Map.of("size", "20x2.4")));

        assertEquals(Status.PASS, resultFor(checker.check(build), "Tire clearance").status());
    }

    @Test
    void isCompatibleRejectsAPartThatWouldFail() {
        BikeBuild build = new BikeBuild("Mid frame");
        build.setPart(PartCategory.FRAME, part(PartCategory.FRAME, Map.of("bbType", "MID")));

        assertTrue(checker.isCompatible(build, part(PartCategory.BOTTOM_BRACKET, Map.of("bbType", "MID"))));
        assertFalse(checker.isCompatible(build, part(PartCategory.BOTTOM_BRACKET, Map.of("bbType", "SPANISH"))));
        assertFalse(build.hasPart(PartCategory.BOTTOM_BRACKET));   // the real build wasn't touched
    }

    @Test
    void monitorRechecksWhenTheBuildChanges() {
        BikeBuild build = new BikeBuild("Observed");
        CompatibilityMonitor monitor = new CompatibilityMonitor(checker);
        build.addListener(monitor);
        assertNull(monitor.latestReport());

        build.setPart(PartCategory.FRAME, part(PartCategory.FRAME, Map.of("bbType", "MID")));
        build.setPart(PartCategory.BOTTOM_BRACKET, part(PartCategory.BOTTOM_BRACKET, Map.of("bbType", "SPANISH")));

        assertEquals(Status.FAIL, monitor.latestReport().overall());
    }
}
