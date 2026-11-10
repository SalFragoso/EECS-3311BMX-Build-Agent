package com.salvador.bmxbuilder.recommend;

import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.RidingStyle;

/**
 * Strategy pattern: the Strategy interface. How good a part is depends on the riding style:
 * street riders want tough parts, race riders want light ones. Each style gets its own scoring,
 * and PartRecommender (the Context) swaps strategies at runtime from the rider's style.
 */
public interface PartRankingStrategy {

    RidingStyle style();

    /** Higher is better for this style. Only compare scores from the same strategy. */
    double score(Part part);
}
