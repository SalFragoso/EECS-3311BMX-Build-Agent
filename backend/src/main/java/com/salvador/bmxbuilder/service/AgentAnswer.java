package com.salvador.bmxbuilder.service;

import java.util.List;

/**
 * Answer from the BMX Q&A assistant (F09).
 *
 * @param answer      the reply (or matching articles if the AI was down)
 * @param sources     titles of the knowledge base articles the answer was based on
 * @param steps       the agent's tool-call log
 * @param aiAvailable false if the LLM couldn't be reached
 * @param tokensUsed  tokens spent on this question
 */
public record AgentAnswer(String answer, List<String> sources, List<String> steps, boolean aiAvailable, int tokensUsed) {
}
