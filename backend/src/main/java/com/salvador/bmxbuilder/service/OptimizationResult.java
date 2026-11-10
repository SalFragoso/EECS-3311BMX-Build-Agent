package com.salvador.bmxbuilder.service;

import com.salvador.bmxbuilder.recommend.BudgetOptimizer;

/**
 * F07 result: the deterministic plan plus the agent's explanation of the trade-offs.
 *
 * @param explanation  the AI's explanation, or a note that it's unavailable
 * @param aiExplained  false if the LLM couldn't be reached (the swaps are still valid)
 */
public record OptimizationResult(BudgetOptimizer.Plan plan, String explanation, boolean aiExplained, int tokensUsed) {
}
