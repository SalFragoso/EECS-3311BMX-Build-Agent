package com.salvador.bmxbuilder.agent;

import java.util.List;

/**
 * What the agent produced.
 *
 * @param text       the model's final answer
 * @param steps      a readable log of every tool call ("search_catalog {category=FRAME} -> ok")
 * @param finished   false if it ran out of steps before giving a final answer
 * @param tokensUsed total tokens across all LLM calls, for the report's cost log
 */
public record AgentResult(String text, List<String> steps, boolean finished, int tokensUsed) {
}
