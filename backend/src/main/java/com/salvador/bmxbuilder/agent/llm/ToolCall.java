package com.salvador.bmxbuilder.agent.llm;

import java.util.Map;

/**
 * The model asking to run a tool.
 * arguments is already parsed into a map; rawArguments is the exact JSON text the model sent,
 * kept so it can be echoed back to the API unchanged.
 */
public record ToolCall(String id, String name, Map<String, Object> arguments, String rawArguments) {
}
