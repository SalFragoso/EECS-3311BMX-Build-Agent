package com.salvador.bmxbuilder.agent;

import com.salvador.bmxbuilder.agent.llm.ChatMessage;
import com.salvador.bmxbuilder.agent.llm.LLMClient;
import com.salvador.bmxbuilder.agent.llm.LLMException;
import com.salvador.bmxbuilder.agent.llm.LLMResponse;
import com.salvador.bmxbuilder.agent.llm.ToolCall;
import com.salvador.bmxbuilder.agent.llm.ToolSpec;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * A fake LLM for tests: plays back replies queued in advance and records what it was sent.
 * Lets the agent loop be tested without the network, an API key or a random model.
 */
public class ScriptedLLMClient implements LLMClient {

    private final Deque<LLMResponse> replies = new ArrayDeque<>();
    public final List<List<ChatMessage>> sentMessages = new ArrayList<>();
    public final List<List<ToolSpec>> sentTools = new ArrayList<>();
    private boolean down;
    private int callCounter;

    /** Queue a reply where the model calls one tool. */
    public ScriptedLLMClient thenCall(String tool, Map<String, Object> args) {
        String id = "call_" + (++callCounter);
        replies.addLast(new LLMResponse(null, List.of(new ToolCall(id, tool, args, "{}")), 100));
        return this;
    }

    /** Queue a final text answer. */
    public ScriptedLLMClient thenAnswer(String text) {
        replies.addLast(LLMResponse.text(text, 50));
        return this;
    }

    /** Every call fails, like when Groq is unreachable. */
    public ScriptedLLMClient down() {
        this.down = true;
        return this;
    }

    @Override
    public LLMResponse complete(List<ChatMessage> messages, List<ToolSpec> tools) {
        if (down) {
            throw new LLMException("Couldn't reach Groq: test says no");
        }
        sentMessages.add(List.copyOf(messages));
        sentTools.add(List.copyOf(tools));
        if (replies.isEmpty()) {
            // models sometimes keep calling tools; this simulates one that never stops
            return new LLMResponse(null, List.of(new ToolCall("loop", "search_catalog", Map.of(), "{}")), 10);
        }
        return replies.removeFirst();
    }

    @Override
    public String modelName() {
        return "scripted-test-model";
    }
}
