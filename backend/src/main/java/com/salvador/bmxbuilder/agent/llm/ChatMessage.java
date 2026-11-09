package com.salvador.bmxbuilder.agent.llm;

import java.util.List;

/**
 * One message in a conversation with the LLM.
 * - SYSTEM: instructions for the model
 * - USER: what the rider typed
 * - ASSISTANT: the model's reply (text, or a request to call tools)
 * - TOOL: the result of a tool call, linked back to the call by toolCallId
 */
public record ChatMessage(Role role, String content, String toolCallId, List<ToolCall> toolCalls) {

    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content, null, List.of());
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content, null, List.of());
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content, null, List.of());
    }

    /** The model asked for these tools to be run (its text, if any, is kept too). */
    public static ChatMessage assistantToolCalls(String content, List<ToolCall> calls) {
        return new ChatMessage(Role.ASSISTANT, content, null, List.copyOf(calls));
    }

    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage(Role.TOOL, content, toolCallId, List.of());
    }
}
