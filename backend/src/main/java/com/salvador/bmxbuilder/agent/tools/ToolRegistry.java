package com.salvador.bmxbuilder.agent.tools;

import com.salvador.bmxbuilder.agent.llm.ToolCall;
import com.salvador.bmxbuilder.agent.llm.ToolSpec;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command pattern: the Invoker. Holds every AgentTool by name and runs the one the LLM asks for.
 * Spring passes in every AgentTool bean it finds, so adding a tool is just adding a @Component.
 * A failing tool never crashes the agent: the error goes back to the model as a result.
 */
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<AgentTool> tools) {
        tools.forEach(this::register);
    }

    public void register(AgentTool tool) {
        tools.put(tool.name(), tool);
    }

    public List<ToolSpec> specs() {
        return tools.values().stream().map(AgentTool::spec).toList();
    }

    /** Only the named tools (an agent task usually needs a subset). Unknown names are ignored. */
    public List<ToolSpec> specs(Collection<String> names) {
        return tools.values().stream()
                .filter(t -> names.contains(t.name()))
                .map(AgentTool::spec)
                .toList();
    }

    public ToolResult execute(ToolCall call) {
        AgentTool tool = tools.get(call.name());
        if (tool == null) {
            return ToolResult.error("Unknown tool '" + call.name() + "'. Available: " + tools.keySet());
        }
        try {
            return tool.execute(call.arguments());
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        } catch (RuntimeException e) {
            return ToolResult.error(tool.name() + " failed: " + e.getMessage());
        }
    }
}
