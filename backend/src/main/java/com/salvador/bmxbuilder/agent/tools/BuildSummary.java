package com.salvador.bmxbuilder.agent.tools;

import com.salvador.bmxbuilder.domain.BikeBuild;
import com.salvador.bmxbuilder.domain.Money;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityReport;
import com.salvador.bmxbuilder.domain.compatibility.RuleResult;

import java.math.RoundingMode;
import java.util.Map;
import java.util.stream.Collectors;

/** Plain-text descriptions of parts and builds for the LLM to read. Kept short to save tokens. */
public final class BuildSummary {

    private BuildSummary() {
    }

    public static String money(Money m) {
        return "$" + m.getAmount().setScale(2, RoundingMode.HALF_UP) + " " + m.getCurrency();
    }

    /** id=1 | Cult Hawk | FRAME | $449.99 CAD | 2150g | styles: PARK, STREET | specs: bbType=MID, ... */
    public static String part(Part p) {
        String styles = p.getStyleTags().stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
        String specs = p.getSpecs().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        return "id=" + p.getId() + " | " + p.getName() + " | " + p.getCategory() + " | " + money(p.getPrice())
                + " | " + Math.round(p.getWeight()) + "g | styles: " + styles + " | specs: " + specs;
    }

    public static String build(BikeBuild build, CompatibilityReport report) {
        StringBuilder out = new StringBuilder();
        out.append("Build ").append(build.getId()).append(" '").append(build.getName()).append("': total ")
                .append(money(build.totalPrice())).append(", ").append(Math.round(build.totalWeight())).append("g\n");

        if (build.getSlots().isEmpty()) {
            out.append("Parts: none yet\n");
        } else {
            out.append("Parts:\n");
            for (Map.Entry<PartCategory, Part> e : build.getSlots().entrySet()) {
                Part p = e.getValue();
                out.append("  ").append(e.getKey()).append(" = id ").append(p.getId()).append(", ")
                        .append(p.getName()).append(", ").append(money(p.getPrice())).append("\n");
            }
        }

        out.append(build.isComplete()
                ? "All required slots are filled.\n"
                : "Missing required slots: " + build.missingSlots() + "\n");

        out.append("Compatibility: ").append(report.overall()).append("\n");
        for (RuleResult r : report.issues()) {
            out.append("  ").append(r.status()).append(" ").append(r.rule()).append(": ").append(r.message()).append("\n");
        }
        return out.toString().trim();
    }
}
