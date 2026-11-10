package com.salvador.bmxbuilder.recommend;

import com.salvador.bmxbuilder.domain.*;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityChecker;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BudgetOptimizerTest {

    private final BudgetOptimizer optimizer = new BudgetOptimizer(new CompatibilityChecker());
    private long nextId = 1;

    private Part part(PartCategory category, String price, double grams, Set<RidingStyle> styles, Map<String, String> specs) {
        Part p = new Part("Test", category.name() + " " + price, category,
                new Money(new BigDecimal(price), "CAD"), grams, "x.png");
        p.getStyleTags().addAll(styles);
        p.getSpecs().putAll(specs);
        try {
            Field f = Part.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(p, nextId++);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return p;
    }

    // ---------- Strategy ----------

    @Test
    void raceStrategyPrefersLighterParts() {
        Part heavy = part(PartCategory.FRAME, "400", 2400, Set.of(RidingStyle.RACE), Map.of());
        Part light = part(PartCategory.FRAME, "400", 1700, Set.of(RidingStyle.RACE), Map.of());

        assertEquals(light, PartRecommender.forStyle(RidingStyle.RACE).rank(List.of(heavy, light)).get(0));
    }

    @Test
    void streetStrategyPrefersPartsMadeForStreet() {
        Part racePart = part(PartCategory.FRAME, "400", 1700, Set.of(RidingStyle.RACE), Map.of());
        Part streetPart = part(PartCategory.FRAME, "400", 2300, Set.of(RidingStyle.STREET), Map.of());

        assertEquals(streetPart, PartRecommender.forStyle(RidingStyle.STREET).rank(List.of(racePart, streetPart)).get(0));
    }

    @Test
    void strategyCanBeSwappedAtRuntime() {
        PartRecommender recommender = PartRecommender.forStyle(RidingStyle.STREET);
        recommender.setStrategy(new RaceRankingStrategy());

        assertEquals(RidingStyle.RACE, recommender.getStrategy().style());
    }

    @Test
    void everyStyleHasAStrategy() {
        for (RidingStyle style : RidingStyle.values()) {
            assertEquals(style, PartRecommender.strategyFor(style).style());
        }
    }

    // ---------- Optimizer ----------

    @Test
    void swapsBringTheBuildUnderBudget() {
        Part pricyBars = part(PartCategory.BARS, "100", 900, Set.of(RidingStyle.STREET), Map.of());
        Part cheapBars = part(PartCategory.BARS, "50", 900, Set.of(RidingStyle.STREET), Map.of());
        BikeBuild build = new BikeBuild("b");
        build.setPart(PartCategory.BARS, pricyBars);

        BudgetOptimizer.Plan plan = optimizer.optimize(build, new BigDecimal("60"), Set.of(), RidingStyle.STREET,
                List.of(pricyBars, cheapBars));

        assertTrue(plan.reachable());
        assertEquals(1, plan.swaps().size());
        assertSame(cheapBars, plan.swaps().get(0).to());
        assertEquals(0, plan.newTotal().getAmount().compareTo(new BigDecimal("50")));
        assertSame(pricyBars, build.getPart(PartCategory.BARS));   // the real build isn't changed
    }

    @Test
    void incompatibleCheaperPartsAreSkipped() {
        Part frame = part(PartCategory.FRAME, "400", 2100, Set.of(RidingStyle.STREET), Map.of("bbType", "MID"));
        Part midBb = part(PartCategory.BOTTOM_BRACKET, "60", 120, Set.of(RidingStyle.STREET), Map.of("bbType", "MID"));
        Part cheapSpanish = part(PartCategory.BOTTOM_BRACKET, "20", 120, Set.of(RidingStyle.STREET), Map.of("bbType", "SPANISH"));
        BikeBuild build = new BikeBuild("b");
        build.setPart(PartCategory.FRAME, frame);
        build.setPart(PartCategory.BOTTOM_BRACKET, midBb);

        BudgetOptimizer.Plan plan = optimizer.optimize(build, new BigDecimal("400"), Set.of(PartCategory.FRAME),
                RidingStyle.STREET, List.of(frame, midBb, cheapSpanish));

        assertTrue(plan.swaps().isEmpty());
        assertFalse(plan.reachable());
        assertTrue(plan.message().contains("unlocking FRAME") || plan.message().contains("no cheaper"));
    }

    @Test
    void styleCriticalPartsAreSwappedLast() {
        // swapping the frame loses its STREET fit; swapping the bars doesn't. Bars should go first.
        Part streetFrame = part(PartCategory.FRAME, "450", 2200, Set.of(RidingStyle.STREET), Map.of());
        Part raceFrame = part(PartCategory.FRAME, "350", 1700, Set.of(RidingStyle.RACE), Map.of());
        Part bars = part(PartCategory.BARS, "100", 900, Set.of(RidingStyle.STREET), Map.of());
        Part cheapBars = part(PartCategory.BARS, "40", 900, Set.of(RidingStyle.STREET), Map.of());
        BikeBuild build = new BikeBuild("b");
        build.setPart(PartCategory.FRAME, streetFrame);
        build.setPart(PartCategory.BARS, bars);

        BudgetOptimizer.Plan plan = optimizer.optimize(build, new BigDecimal("500"), Set.of(), RidingStyle.STREET,
                List.of(streetFrame, raceFrame, bars, cheapBars));

        assertEquals(1, plan.swaps().size());
        assertEquals(PartCategory.BARS, plan.swaps().get(0).slot());
        assertTrue(plan.reachable());
    }

    @Test
    void alreadyUnderBudgetSaysSo() {
        BikeBuild build = new BikeBuild("b");
        build.setPart(PartCategory.SEAT, part(PartCategory.SEAT, "40", 300, Set.of(), Map.of()));

        BudgetOptimizer.Plan plan = optimizer.optimize(build, new BigDecimal("100"), Set.of(), RidingStyle.STREET, List.of());

        assertTrue(plan.reachable());
        assertTrue(plan.message().startsWith("Already under budget by $60.00"));
    }

    @Test
    void everythingLockedIsAnError() {
        BikeBuild build = new BikeBuild("b");
        build.setPart(PartCategory.SEAT, part(PartCategory.SEAT, "40", 300, Set.of(), Map.of()));

        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(build, new BigDecimal("10"),
                Set.of(PartCategory.SEAT), RidingStyle.STREET, List.of()));
    }

    @Test
    void zeroBudgetIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(new BikeBuild("b"), BigDecimal.ZERO,
                Set.of(), RidingStyle.STREET, List.of()));
    }
}
