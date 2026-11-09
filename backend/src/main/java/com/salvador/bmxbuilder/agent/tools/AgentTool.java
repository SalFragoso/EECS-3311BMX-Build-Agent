package com.salvador.bmxbuilder.agent.tools;

import com.salvador.bmxbuilder.agent.llm.ToolSpec;

import java.util.Map;

/**
 * Command pattern: the Command interface. Each tool wraps one deterministic operation
 * (search the catalog, add a part, check compatibility) behind the same execute() call,
 * so the agent can run whichever one the LLM asks for without knowing what's inside.
 * ToolRegistry is the Invoker; the services/repositories each tool calls are the Receivers.
 */
public interface AgentTool {

    /** The name the LLM uses to call this tool, e.g. "search_catalog". */
    String name();

    /** Description + JSON Schema of the arguments, sent to the LLM. */
    ToolSpec spec();

    /** Runs the tool. Throw IllegalArgumentException for bad input; the registry turns it into an error result. */
    ToolResult execute(Map<String, Object> args);
}
