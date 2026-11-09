package com.salvador.bmxbuilder.agent.tools;

/**
 * Outcome of one tool call. content is plain text the LLM reads; errors are sent back too,
 * so the model can correct itself (e.g. after naming a part id that doesn't exist).
 */
public record ToolResult(boolean success, String content) {

    public static ToolResult ok(String content) {
        return new ToolResult(true, content);
    }

    public static ToolResult error(String message) {
        return new ToolResult(false, message);
    }

    /** What actually goes back to the model as the tool message. */
    public String toMessageContent() {
        return success ? content : "ERROR: " + content;
    }
}
