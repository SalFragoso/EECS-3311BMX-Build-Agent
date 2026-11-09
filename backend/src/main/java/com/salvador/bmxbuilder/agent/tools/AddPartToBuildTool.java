package com.salvador.bmxbuilder.agent.tools;

import com.salvador.bmxbuilder.agent.llm.ToolSpec;
import com.salvador.bmxbuilder.data.BuildRepository;
import com.salvador.bmxbuilder.data.PartRepository;
import com.salvador.bmxbuilder.domain.BikeBuild;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityChecker;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Lets the agent put a part into a slot of a build (replacing what's there), then shows it the
 * updated build with its compatibility issues so it can fix bad picks.
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
                "Put a catalog part into a slot of the build, replacing any part already there. "
                        + "Returns the updated build, its total and any compatibility problems.",
                Schemas.object(Schemas.props(
                        "build_id", Schemas.integer("The build to change"),
                        "slot", Schemas.enumOf(PartCategory.class, "The slot to fill; must match the part's category"),
                        "part_id", Schemas.integer("A part id returned by search_catalog")),
                        List.of("build_id", "slot", "part_id")));
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        long buildId = ToolArgs.requiredLong(args, "build_id");
        PartCategory slot = ToolArgs.requiredEnum(args, "slot", PartCategory.class);
        long partId = ToolArgs.requiredLong(args, "part_id");

        BikeBuild build = builds.findById(buildId)
                .orElseThrow(() -> new IllegalArgumentException("No build with id " + buildId));
        Part part = parts.findById(partId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No part with id " + partId + ". Use search_catalog to find real ids."));

        build.setPart(slot, part);   // throws IllegalArgumentException if the part's category doesn't match
        BikeBuild saved = builds.save(build);
        return ToolResult.ok("Added " + part.getName() + " to " + slot + ".\n"
                + BuildSummary.build(saved, checker.check(saved)));
    }
}
