package com.salvador.bmxbuilder.service;

import java.util.List;

/**
 * Result of the AI build planner (F02).
 *
 * @param build      the build the agent filled, re-checked by the deterministic compatibility checker
 * @param summary    the agent's explanation of its picks (or the fallback message if the AI was down)
 * @param steps      the agent's tool-call log, shown so the rider can see what it did
 * @param finished   false if the agent hit its step limit or the AI was unavailable
 * @param aiAvailable false if the LLM couldn't be reached
 * @param tokensUsed tokens spent on this request
 */
public record PlanResult(BuildView build,
                         String summary,
                         List<String> steps,
                         boolean finished,
                         boolean aiAvailable,
                         int tokensUsed) {
}
