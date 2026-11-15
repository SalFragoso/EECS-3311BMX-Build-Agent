package com.salvador.bmxbuilder.agent.tools;

import com.salvador.bmxbuilder.agent.llm.ToolSpec;
import com.salvador.bmxbuilder.data.BuildRepository;
import com.salvador.bmxbuilder.data.PartRepository;
import com.salvador.bmxbuilder.domain.BikeBuild;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityChecker;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lets the agent put parts into slots of a build (replacing what's there), then shows it the
 * updated build with its compatibility issues so it can fix bad picks. It takes one part
 * (slot + part_id) or several at once (parts), so a whole build can be filled in a single call.
 */
@Component
public class AddPartToBuildTool implements AgentTool {

    private final BuildRepository builds;
    private final PartRepository parts;
    private final CompatibilityChecker checker;

    public AddPartToBuildTool(BuildRepository builds, PartRepository parts, CompatibilityChecker checker) {
        this.builds = builds;
        this.parts = parts;
        this.checker = checker;
    }

    @Override
    public String name() {
        return "add_part_to_build";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(),
                "Put catalog parts into slots of the build, replacing any part already there. Give either "
                        + "slot + part_id for one part, or parts for several at once (preferred: add all your picks "
                        + "in one call). Returns the updated build, its total and any compatibility problems.",
                Schemas.object(Schemas.props(
                        "build_id", Schemas.integer("The build to change"),
                        "slot", Schemas.enumOf(PartCategory.class, "The slot to fill; must match the part's category"),
                        "part_id", Schemas.integer("A part id returned by search_catalog"),
                        "parts", Map.of("type", "array",
                                "description", "Several parts to add at once",
                                "items", Schemas.object(Schemas.props(
                                        "slot", Schemas.enumOf(PartCategory.class, "The slot to fill"),
                                        "part_id", Schemas.integer("A part id returned by search_catalog")),
                                        List.of("slot", "part_id")))),
                        List.of("build_id")));
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        long buildId = ToolArgs.requiredLong(args, "build_id");
        List<Map<String, Object>> picks = picks(args);

        BikeBuild build = builds.findById(buildId)
                .orElseThrow(() -> new IllegalArgumentException("No build with id " + buildId));

        if (picks.size() == 1) {
            // one part: any problem is the whole result, as before
            Map<String, Object> pick = picks.get(0);
            Part part = add(build, pick);
            BikeBuild saved = builds.save(build);
            return ToolResult.ok("Added " + part.getName() + " to " + pick.get("slot").toString().toUpperCase() + ".\n"
                    + BuildSummary.build(saved, checker.check(saved)));
        }

        // several parts: add the good ones, report the bad ones, so one typo doesn't waste the whole call
        List<String> added = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Map<String, Object> pick : picks) {
            try {
                Part part = add(build, pick);
                added.add(part.getName());
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
        if (added.isEmpty()) {
            return ToolResult.error("Nothing was added: " + String.join("; ", problems));
        }
        BikeBuild saved = builds.save(build);
        String report = "Added " + added.size() + " parts: " + String.join(", ", added) + ".\n";
        if (!problems.isEmpty()) {
            report += "Not added: " + String.join("; ", problems) + "\n";
        }
        return ToolResult.ok(report + BuildSummary.build(saved, checker.check(saved)));
    }

    /** The parts to add, from either the parts list or the single slot + part_id. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> picks(Map<String, Object> args) {
        Object list = args.get("parts");
        if (list instanceof List<?> items && !items.isEmpty()) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : items) {
                if (!(item instanceof Map)) {
                    throw new IllegalArgumentException("Each entry in parts needs a slot and a part_id");
                }
                out.add((Map<String, Object>) item);
            }
            return out;
        }
        if (!args.containsKey("slot") && !args.containsKey("part_id")) {
            throw new IllegalArgumentException("Give slot + part_id, or a parts list");
        }
        return List.of(args);
    }

    /** Puts one part in its slot. Throws IllegalArgumentException with a message the agent can act on. */
    private Part add(BikeBuild build, Map<String, Object> pick) {
        PartCategory slot = ToolArgs.requiredEnum(pick, "slot", PartCategory.class);
        long partId = ToolArgs.requiredLong(pick, "part_id");
        Part part = parts.findById(partId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No part with id " + partId + ". Use search_catalog to find real ids."));
        build.setPart(slot, part);   // throws IllegalArgumentException if the part's category doesn't match
        return part;
    }
}
