package com.salvador.bmxbuilder.agent;

import java.util.Set;

/**
 * One job for the agent.
 *
 * @param conversationId groups messages for memory (follow-up questions see earlier turns)
 * @param systemPrompt   instructions for this kind of task (planner, Q&A)
 * @param userMessage    what the rider asked; this is what gets remembered
 * @param context        extra info sent with this message only (e.g. knowledge passages); not remembered
 * @param toolNames      which tools the agent may use for this task
 * @param maxSteps       how many LLM calls before giving up
 */
public record AgentTask(String conversationId,
                        String systemPrompt,
                        String userMessage,
                        String context,
                        Set<String> toolNames,
                        int maxSteps) {
}
