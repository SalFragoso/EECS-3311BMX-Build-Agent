package com.salvador.bmxbuilder.agent.llm;

import java.util.List;

/**
 * What the model sent back: either a final text answer, or tool calls it wants run first.
 * tokensUsed is what this one request cost (prompt + completion), for the report's token log.
 */
public record LLMResponse(String text, List<ToolCall> toolCalls, int tokensUsed) {

    public LLMResponse {
        toolCalls = (toolCalls == null) ? List.of() : List.copyOf(toolCalls);
    }

    public static LLMResponse text(String text, int tokensUsed) {
        return new LLMResponse(text, List.of(), tokensUsed);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
