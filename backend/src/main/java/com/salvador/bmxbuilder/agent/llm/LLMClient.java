package com.salvador.bmxbuilder.agent.llm;

import java.util.List;

/**
 * Adapter pattern: the Target interface. The agent only depends on this, never on Groq directly,
 * so the provider can be swapped (or faked in tests) without touching the agent.
 */
public interface LLMClient {

    /**
     * Sends the conversation and the tools the model may use; returns the model's reply.
     * Throws LLMException if the provider can't be reached or rejects the request.
     */
    LLMResponse complete(List<ChatMessage> messages, List<ToolSpec> tools);

    String modelName();
}
