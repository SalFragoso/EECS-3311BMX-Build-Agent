package com.salvador.bmxbuilder.recommend;

import com.salvador.bmxbuilder.domain.BikeBuild;
import com.salvador.bmxbuilder.domain.Money;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import com.salvador.bmxbuilder.domain.RidingStyle;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityChecker;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * F07, the deterministic part: finds cheaper parts that still fit and picks swaps that reach the
 * budget while giving up as little as possible for the rider's style.
 *
 * For each unlocked slot it finds the best cheaper part (ranked by the style's Strategy) that is
 * still compatible. Then it applies the swaps that lose the least "style score per dollar saved"
 * first, re-checking compatibility after each one, until the build is under budget.
 */
@Component
public class BudgetOptimizer {

    private final CompatibilityChecker checker;

    public BudgetOptimizer(CompatibilityChecker checker) {
        this.checker = checker;
    }

    public record Swap(PartCategory slot, Part from, Part to, Money savings, String reason) {
    }

    public record Plan(Money originalTotal,
                       Money newTotal,
                       Money target,
                       List<Swap> swaps,
                       boolean reachable,
                       String message) {
    }

    private record Option(PartCategory slot, Part from, Part to, BigDecimal saving, double styleLoss) {
        double lossPerDollar() {
            return styleLoss / saving.doubleValue();
        }
    }

    public Plan optimize(BikeBuild build, BigDecimal target, Set<PartCategory> locked,
                         RidingStyle style, List<Part> catalog) {
        if (target == null || target.signum() <= 0) {
            throw new IllegalArgumentException("Enter a target budget above $0");
        }
        Money targetMoney = new Money(target.setScale(2, RoundingMode.HALF_UP), "CAD");
        Money original = build.totalPrice();
        if (build.getSlots().isEmpty()) {
            throw new IllegalArgumentException("The build has no parts yet, so there's nothing to optimize");
        }
        if (original.getAmount().compareTo(target) <= 0) {
            BigDecimal under = target.subtract(original.getAmount());
            return new Plan(original, original, targetMoney, List.of(), true,
                    "Already under budget by $" + under.setScale(2, RoundingMode.HALF_UP)
                            + ". You could spend that on upgrades instead.");
        }
        List<PartCategory> unlocked = build.getSlots().keySet().stream()
                .filter(slot -> locked == null || !locked.contains(slot))
                .toList();
        if (unlocked.isEmpty()) {
            throw new IllegalArgumentException("Every part is locked. Unlock at least one so there's something to swap.");
        }

        PartRecommender recommender = PartRecommender.forStyle(style);

        // 1. best cheaper, compatible option for each unlocked slot
        List<Option> options = new ArrayList<>();
        for (PartCategory slot : unlocked) {
            Part current = build.getPart(slot);
            List<Part> cheaper = catalog.stream()
                    .filter(p -> p.getCategory() == slot)
                    .filter(p -> p.getPrice().getAmount().compareTo(current.getPrice().getAmount()) < 0)
                    .toList();
            for (Part candidate : recommender.rank(cheaper)) {
                if (checker.isCompatible(build, candidate)) {
                    BigDecimal saving = current.getPrice().getAmount().subtract(candidate.getPrice().getAmount());
                    double loss = Math.max(0, recommender.score(current) - recommender.score(candidate));
                    options.add(new Option(slot, current, candidate, saving, loss));
                    break;
                }
            }
        }

        // 2. apply the cheapest-in-style swaps first until the budget is met
        options.sort(Comparator.comparingDouble(Option::lossPerDollar)
                .thenComparing(Option::saving, Comparator.reverseOrder()));
        BikeBuild working = build;
        List<Swap> swaps = new ArrayList<>();
        for (Option o : options) {
            if (working.totalPrice().getAmount().compareTo(target) <= 0) {
                break;
            }
            if (!checker.isCompatible(working, o.to())) {
                continue;   // an earlier swap changed what fits
            }
            working = working.withPart(o.to());
            swaps.add(new Swap(o.slot(), o.from(), o.to(), new Money(o.saving(), "CAD"), reason(o, style)));
        }

        Money newTotal = working.totalPrice();
        boolean reachable = newTotal.getAmount().compareTo(target) <= 0;
        String message = reachable
                ? swaps.size() + " swap(s) bring the build to $" + newTotal.getAmount() + ", under the $" + targetMoney.getAmount() + " target."
                : "The lowest total with these locks is $" + newTotal.getAmount() + lockedNote(build, locked, catalog) + ".";
        return new Plan(original, newTotal, targetMoney, swaps, reachable, message);
    }

    private static String reason(Option o, RidingStyle style) {
        String fit = o.to().getStyleTags().contains(style) ? "still made for " : "not specifically made for ";
        return "Saves $" + o.saving().setScale(2, RoundingMode.HALF_UP) + "; " + fit + style.name().toLowerCase()
                + (o.styleLoss() == 0 ? ", no loss for your style" : "");
    }

    /** Which locked slots have cheaper options, i.e. are in the way of the budget. */
    private static String lockedNote(BikeBuild build, Set<PartCategory> locked, List<Part> catalog) {
        if (locked == null || locked.isEmpty()) {
            return "; there are no cheaper compatible parts left in the catalog";
        }
        List<String> blocking = new ArrayList<>();
        for (PartCategory slot : locked) {
            Part current = build.getPart(slot);
            if (current != null && catalog.stream().anyMatch(p -> p.getCategory() == slot
                    && p.getPrice().getAmount().compareTo(current.getPrice().getAmount()) < 0)) {
                blocking.add(slot.name());
            }
        }
        return blocking.isEmpty()
                ? "; there are no cheaper compatible parts left in the catalog"
                : "; unlocking " + String.join(", ", blocking) + " could save more";
    }
}
