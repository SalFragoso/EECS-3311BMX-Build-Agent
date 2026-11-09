package com.salvador.bmxbuilder.agent.llm;

import java.util.Map;

/**
 * Describes a tool to the model: its name, what it does, and its parameters as a JSON Schema
 * (written as nested maps, e.g. {"type": "object", "properties": {...}}).
 */
public record ToolSpec(String name, String description, Map<String, Object> parameters) {
}
