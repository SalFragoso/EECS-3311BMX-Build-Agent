package com.salvador.bmxbuilder.recommend;

import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.RidingStyle;

import java.util.Comparator;
import java.util.List;

/**
 * Strategy pattern: the Context. Ranks parts using whichever strategy matches the rider's style.
 * The budget optimizer uses it to decide which parts matter most for that style.
 */
public class PartRecommender {

    private PartRankingStrategy strategy;

    public PartRecommender(PartRankingStrategy strategy) {
        this.strategy = strategy;
    }

    public static PartRecommender forStyle(RidingStyle style) {
        return new PartRecommender(strategyFor(style));
    }

    public static PartRankingStrategy strategyFor(RidingStyle style) {
        return switch (style) {
            case STREET -> new StreetRankingStrategy();
            case PARK -> new ParkRankingStrategy();
            case RACE -> new RaceRankingStrategy();
            case DIRT -> new DirtRankingStrategy();
            case FLATLAND -> new FlatlandRankingStrategy();
        };
    }

    public void setStrategy(PartRankingStrategy strategy) {
        this.strategy = strategy;
    }

    public PartRankingStrategy getStrategy() {
        return strategy;
    }

    public double score(Part part) {
        return strategy.score(part);
    }

    /** Best first; ties go to the cheaper part. */
    public List<Part> rank(List<Part> parts) {
        return parts.stream()
                .sorted(Comparator.comparingDouble((Part p) -> strategy.score(p)).reversed()
                        .thenComparing(p -> p.getPrice().getAmount()))
                .toList();
    }
}
