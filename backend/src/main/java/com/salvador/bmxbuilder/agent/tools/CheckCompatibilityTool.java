package com.salvador.bmxbuilder.agent.tools;

import com.salvador.bmxbuilder.agent.llm.ToolSpec;
import com.salvador.bmxbuilder.data.BuildRepository;
import com.salvador.bmxbuilder.domain.BikeBuild;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityChecker;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Lets the agent look at a build and run the full compatibility check (F05) on it. */
@Component
public class CheckCompatibilityTool implements AgentTool {

    private final BuildRepository builds;
    private final CompatibilityChecker checker;

    public CheckCompatibilityTool(BuildRepository builds, CompatibilityChecker checker) {
        this.builds = builds;
        this.checker = checker;
    }

    @Override
    public String name() {
        return "check_compatibility";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(),
                "Show a build's parts, total price, missing slots and compatibility problems.",
                Schemas.object(Schemas.props("build_id", Schemas.integer("The build to check")),
                        List.of("build_id")));
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        long buildId = ToolArgs.requiredLong(args, "build_id");
        BikeBuild build = builds.findById(buildId)
                .orElseThrow(() -> new IllegalArgumentException("No build with id " + buildId));
        return ToolResult.ok(BuildSummary.build(build, checker.check(build)));
    }
}
