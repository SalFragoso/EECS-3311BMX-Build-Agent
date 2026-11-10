package com.salvador.bmxbuilder.recommend;

import com.salvador.bmxbuilder.domain.RidingStyle;

import java.util.List;

/**
 * F01 result.
 *
 * @param ranked     styles best first, each with a 0-100 score and a reason
 * @param topTube    recommended frame top tube range from the height table, e.g. 20.5" to 21"
 * @param aiBased    true if the LLM ranked the styles; false means the rule-based fallback was used
 * @param close      true when the top two styles are within 10 points, so the rider should pick
 * @param note       shown to the rider (e.g. "Basic recommendation: AI unavailable")
 */
public record StyleRecommendation(List<RankedStyle> ranked,
                                  String topTube,
                                  boolean aiBased,
                                  boolean close,
                                  String note,
                                  int tokensUsed) {

    public record RankedStyle(RidingStyle style, int score, String reason) {
    }

    public RidingStyle best() {
        return ranked.isEmpty() ? null : ranked.get(0).style();
    }
}
