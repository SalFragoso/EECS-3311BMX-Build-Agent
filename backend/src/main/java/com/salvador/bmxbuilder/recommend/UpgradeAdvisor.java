package com.salvador.bmxbuilder.recommend;

import com.salvador.bmxbuilder.agent.AgentOrchestrator;
import com.salvador.bmxbuilder.agent.AgentResult;
import com.salvador.bmxbuilder.agent.AgentTask;
import com.salvador.bmxbuilder.agent.JsonReplies;
import com.salvador.bmxbuilder.agent.Prompts;
import com.salvador.bmxbuilder.agent.llm.LLMException;
import com.salvador.bmxbuilder.domain.BikeBuild;
import com.salvador.bmxbuilder.domain.Money;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import com.salvador.bmxbuilder.domain.RidingStyle;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityChecker;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityReport;
import com.salvador.bmxbuilder.domain.compatibility.RuleResult;
import com.salvador.bmxbuilder.domain.compatibility.Status;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * F11 Upgrade Path Advisor, hybrid:
 *  1. (optional) the agent matches a plain-text description of the rider's bike to catalog parts
 *  2. deterministic code scores every possible upgrade for the rider's style (ranking Strategy),
 *     checks compatibility, and bundles parts that depend on each other (e.g. cranks + bottom bracket)
 *  3. the agent groups the affordable upgrades into stages; the code validates the stages
 *     (budget, no duplicates, compatibility after each stage) and falls back to value order if needed
 */
@Component
public class UpgradeAdvisor {

    static final int STOCK_BONUS = 40;        // replacing a stock/unknown part is high impact
    static final double QUALITY_WEIGHT = 20;  // pricier part in the same style is usually better made
    static final int MAX_STAGES = 3;

    private final AgentOrchestrator agent;
    private final CompatibilityChecker checker;

    public UpgradeAdvisor(AgentOrchestrator agent, CompatibilityChecker checker) {
        this.agent = agent;
        this.checker = checker;
    }

    // ---------- result types ----------

    /** One thing to buy. A bundle replaces several slots at once because the parts depend on each other. */
    public record Option(int number, List<PartCategory> slots, List<String> replacing, List<Part> parts,
                         Money cost, double gain, String reason) {
    }

    public record Stage(int number, List<Option> options, Money cost, Status compatibility, String reason) {
    }

    public record Plan(Long buildId, String bikeName, RidingStyle style, Money budget, List<Stage> stages,
                       Money totalCost, String summary, boolean aiStaged, List<String> notes,
                       List<String> steps, int tokensUsed) {
    }

    /** Step 1 result: what the agent recognised in the description. */
    public record Match(Map<PartCategory, Part> matched, List<PartCategory> stock, boolean ok,
                        String problem, List<String> steps, int tokensUsed) {
    }

    // ---------- step 1: description -> catalog parts (AI) ----------

    public Match matchDescription(String description, List<Part> catalog) {
        Map<Long, Part> byId = new HashMap<>();
        catalog.forEach(p -> byId.put(p.getId(), p));
        List<String> steps = new ArrayList<>();
        int tokens = 0;
        try {
            for (int attempt = 0; attempt < 2; attempt++) {   // unreadable output gets one retry
                AgentResult r = agent.run(new AgentTask("upgrade-match-" + UUID.randomUUID(), Prompts.bikeMatcher(),
                        "My current bike: " + description.trim(), null, Set.of("search_catalog"), 8));
                steps.addAll(r.steps());
                tokens += r.tokensUsed();
                Map<String, Object> json = JsonReplies.object(r.text());
                if (json == null) {
                    continue;
                }
                Map<PartCategory, Part> matched = new EnumMap<>(PartCategory.class);
                if (json.get("matched") instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> m && m.get("partId") instanceof Number id) {
                            Part p = byId.get(id.longValue());
                            if (p != null) {
                                matched.put(p.getCategory(), p);   // trust the part's real category, not the model's
                            }
                        }
                    }
                }
                List<PartCategory> stock = new ArrayList<>();
                for (PartCategory slot : BikeBuild.REQUIRED_SLOTS) {
                    if (!matched.containsKey(slot)) {
                        stock.add(slot);
                    }
                }
                return new Match(matched, stock, true, null, steps, tokens);
            }
            return new Match(Map.of(), List.of(), false,
                    "The AI couldn't read your description. Pick your current parts in the configurator and use that build instead.",
                    steps, tokens);
        } catch (LLMException e) {
            return new Match(Map.of(), List.of(), false, "The AI is unavailable (" + e.getMessage()
                    + "), so it can't read a description. Pick your current parts in the configurator and use that build instead.",
                    steps, tokens);
        }
    }

    // ---------- step 2: scored, compatible options (deterministic) ----------

    /** How much better a candidate is than what's there now, for this style. Positive = an upgrade. */
    static double gain(PartRecommender rec, Part current, Part candidate) {
        if (current == null) {
            return rec.score(candidate) + STOCK_BONUS;
        }
        double styleDiff = rec.score(candidate) - rec.score(current);
        double priceRatio = candidate.getPrice().getAmount().doubleValue() / current.getPrice().getAmount().doubleValue();
        return styleDiff + QUALITY_WEIGHT * Math.log(priceRatio);
    }

    /** The best option per slot, best value (gain per dollar) first. notes collects frame limits. */
    public List<Option> options(BikeBuild bike, RidingStyle style, List<Part> catalog, List<String> notes) {
        PartRecommender rec = PartRecommender.forStyle(style);
        Set<PartCategory> slots = EnumSet.copyOf(BikeBuild.REQUIRED_SLOTS);
        slots.addAll(bike.getSlots().keySet());

        List<Option> best = new ArrayList<>();
        for (PartCategory slot : slots) {
            Part current = bike.getPart(slot);
            List<Part> candidates = catalog.stream()
                    .filter(p -> p.getCategory() == slot)
                    .filter(p -> current == null || (!p.getId().equals(current.getId())
                            && p.getPrice().getAmount().compareTo(current.getPrice().getAmount()) > 0))
                    .filter(p -> gain(rec, current, p) > 0)
                    .sorted(Comparator.comparingDouble((Part p) -> gain(rec, current, p)).reversed())
                    .toList();

            Option pick = null;
            for (Part candidate : candidates) {
                Option o = asOption(bike, rec, slot, current, candidate, catalog, notes);
                if (o != null && (pick == null || value(o) > value(pick))) {
                    pick = o;
                }
            }
            if (pick != null) {
                best.add(pick);
            }
        }
        best.sort(Comparator.comparingDouble(UpgradeAdvisor::value).reversed());
        List<Option> numbered = new ArrayList<>();
        for (int i = 0; i < best.size(); i++) {
            Option o = best.get(i);
            numbered.add(new Option(i + 1, o.slots(), o.replacing(), o.parts(), o.cost(), o.gain(), o.reason()));
        }
        return numbered;
    }

    /** A single-part option if it fits; a two-part bundle if one partner fixes the clash; else null. */
    private Option asOption(BikeBuild bike, PartRecommender rec, PartCategory slot, Part current, Part candidate,
                            List<Part> catalog, List<String> notes) {
        BikeBuild withCandidate = bike.withPart(candidate);
        List<RuleResult> clashes = clashesWith(checker.check(withCandidate), candidate);
        double g = gain(rec, current, candidate);
        String styleNote = styleNote(candidate, rec.getStrategy().style());
        if (clashes.isEmpty()) {
            return new Option(0, List.of(slot), List.of(name(current)), List.of(candidate), candidate.getPrice(), g,
                    (current == null ? "Replaces a stock part; " : "Better than your " + current.getName() + "; ") + styleNote);
        }
        // Dependent parts: find the other slot in the failing rule and a part there that makes everything fit
        RuleResult clash = clashes.get(0);
        for (Part involved : clash.parts()) {
            if (involved == candidate || involved.getCategory() == slot) {
                continue;
            }
            PartCategory partnerSlot = involved.getCategory();
            if (partnerSlot == PartCategory.FRAME) {
                String prefix = "Your frame limits the " + slot.name().toLowerCase().replace('_', ' ') + " upgrade";
                if (notes.stream().noneMatch(n -> n.startsWith(prefix))) {   // one note per slot
                    notes.add(prefix + " (" + clash.message() + "). A new frame could be a later stage.");
                }
                return null;
            }
            Part partnerCurrent = bike.getPart(partnerSlot);
            for (Part partner : rec.rank(catalog.stream().filter(p -> p.getCategory() == partnerSlot).toList())) {
                BikeBuild both = withCandidate.withPart(partner);
                CompatibilityReport bothReport = checker.check(both);
                // the partner must make the clashing rule actually pass (unknown specs only give a warning)
                boolean fixed = bothReport.results().stream()
                        .anyMatch(r -> r.rule().equals(clash.rule()) && r.status() == Status.PASS);
                if (fixed && clashesWith(bothReport, candidate).isEmpty() && clashesWith(bothReport, partner).isEmpty()) {
                    Money cost = candidate.getPrice().add(partner.getPrice());
                    double partnerGain = Math.max(0, gain(rec, partnerCurrent, partner));
                    return new Option(0, List.of(slot, partnerSlot), List.of(name(current), name(partnerCurrent)),
                            List.of(candidate, partner), cost, g + partnerGain,
                            "Needs a matching " + partnerSlot.name().toLowerCase().replace('_', ' ')
                                    + " (" + clash.rule().toLowerCase() + "), so they're bought together; " + styleNote);
                }
            }
        }
        return null;
    }

    private static String styleNote(Part p, RidingStyle style) {
        if (p.getStyleTags().contains(style)) {
            return "made for " + style.name().toLowerCase();
        }
        if (p.getStyleTags().isEmpty()) {
            return "an all-round part";
        }
        String tags = p.getStyleTags().stream().map(t -> t.name().toLowerCase()).sorted()
                .reduce((a, b) -> a + "/" + b).orElse("");
        return "made for " + tags + ", not " + style.name().toLowerCase() + ", but the best that fits";
    }

    private static List<RuleResult> clashesWith(CompatibilityReport report, Part part) {
        return report.results().stream()
                .filter(r -> r.status() == Status.FAIL && r.parts().contains(part))
                .toList();
    }

    private static double value(Option o) {
        return o.gain() / Math.max(1, o.cost().getAmount().doubleValue());
    }

    private static String name(Part p) {
        return p == null ? "stock part" : p.getName();
    }

    // ---------- step 3: stages (AI, validated, with a deterministic fallback) ----------

    public Plan advise(BikeBuild bike, BigDecimal budget, RidingStyle style, List<Part> catalog,
                       List<String> priorSteps, int priorTokens) {
        if (budget == null || budget.signum() <= 0) {
            throw new IllegalArgumentException("Enter an upgrade budget above $0");
        }
        RidingStyle riderStyle = (style == null) ? RidingStyle.STREET : style;
        Money budgetMoney = new Money(budget.setScale(2, RoundingMode.HALF_UP), "CAD");
        List<String> notes = new ArrayList<>();
        List<String> steps = new ArrayList<>(priorSteps);

        List<Option> all = options(bike, riderStyle, catalog, notes);
        if (all.isEmpty()) {
            notes.add("No parts in the catalog beat your current ones for " + riderStyle.name().toLowerCase() + ".");
            return new Plan(bike.getId(), bike.getName(), riderStyle, budgetMoney, List.of(), zero(),
                    "Your bike is already as good as the catalog gets for this style.", false, notes, steps, priorTokens);
        }

        List<Option> affordable = selectWithinBudget(bike, all, budget);
        if (affordable.isEmpty()) {
            Option cheapest = all.stream().min(Comparator.comparing(o -> o.cost().getAmount())).orElseThrow();
            notes.add("The budget is too low for any upgrade. The cheapest high-impact one is option "
                    + cheapest.number() + " at $" + cheapest.cost().getAmount() + ".");
            Stage only = new Stage(1, List.of(cheapest), cheapest.cost(),
                    checker.check(apply(bike, List.of(cheapest))).overall(), "Save up for this one first");
            return new Plan(bike.getId(), bike.getName(), riderStyle, budgetMoney, List.of(only), cheapest.cost(),
                    "Your budget doesn't cover any upgrade yet; this is the best one to save for.", false, notes, steps, priorTokens);
        }

        int tokens = priorTokens;
        List<Stage> stages = null;
        String summary = null;
        boolean aiStaged = false;
        try {
            AgentResult r = agent.run(new AgentTask("upgrade-stage-" + UUID.randomUUID(), Prompts.upgradeStager(),
                    "Group my upgrades into stages.", facts(bike, riderStyle, budgetMoney, affordable), Set.of(), 2));
            tokens += r.tokensUsed();
            Map<String, Object> json = JsonReplies.object(r.text());
            stages = validStages(bike, json, affordable, budget);
            if (stages != null) {
                aiStaged = true;
                summary = json.get("summary") == null ? null : String.valueOf(json.get("summary"));
            } else {
                notes.add("The AI's stages didn't pass the checks, so upgrades are staged by value instead.");
            }
        } catch (LLMException e) {
            notes.add("AI unavailable (" + e.getMessage() + "): upgrades are staged by value for your style.");
        }
        if (stages == null) {
            stages = valueStages(bike, affordable);
        }
        Money total = zero();
        for (Stage s : stages) {
            total = total.add(s.cost());
        }
        if (summary == null || summary.isBlank()) {
            summary = stages.size() + " stage(s), $" + total.getAmount() + " of your $" + budgetMoney.getAmount()
                    + " budget, best improvement for the money first.";
        }
        return new Plan(bike.getId(), bike.getName(), riderStyle, budgetMoney, stages, total, summary, aiStaged,
                notes, steps, tokens);
    }

    /** Best value first; each slot once; stays compatible; total within budget. */
    List<Option> selectWithinBudget(BikeBuild bike, List<Option> options, BigDecimal budget) {
        List<Option> chosen = new ArrayList<>();
        Set<PartCategory> used = EnumSet.noneOf(PartCategory.class);
        BigDecimal spent = BigDecimal.ZERO;
        BikeBuild working = bike;
        for (Option o : options) {
            if (o.slots().stream().anyMatch(used::contains)) {
                continue;
            }
            if (spent.add(o.cost().getAmount()).compareTo(budget) > 0) {
                continue;
            }
            BikeBuild next = apply(working, List.of(o));
            boolean fits = o.parts().stream().allMatch(p -> clashesWith(checker.check(next), p).isEmpty());
            if (!fits) {
                continue;
            }
            chosen.add(o);
            used.addAll(o.slots());
            spent = spent.add(o.cost().getAmount());
            working = next;
        }
        return chosen;
    }

    /** Checks the AI's grouping; null if it isn't usable. */
    List<Stage> validStages(BikeBuild bike, Map<String, Object> json, List<Option> affordable, BigDecimal budget) {
        if (json == null || !(json.get("stages") instanceof List<?> rawStages) || rawStages.isEmpty()
                || rawStages.size() > MAX_STAGES) {
            return null;
        }
        Map<Integer, Option> byNumber = new HashMap<>();
        affordable.forEach(o -> byNumber.put(o.number(), o));
        Set<Integer> seen = new HashSet<>();
        List<Stage> stages = new ArrayList<>();
        BikeBuild working = bike;
        BigDecimal total = BigDecimal.ZERO;
        for (Object raw : rawStages) {
            if (!(raw instanceof Map<?, ?> m) || !(m.get("options") instanceof List<?> nums) || nums.isEmpty()) {
                return null;
            }
            List<Option> opts = new ArrayList<>();
            for (Object n : nums) {
                if (!(n instanceof Number num) || !byNumber.containsKey(num.intValue()) || !seen.add(num.intValue())) {
                    return null;   // unknown or repeated option
                }
                opts.add(byNumber.get(num.intValue()));
            }
            Money cost = sum(opts);
            total = total.add(cost.getAmount());
            working = apply(working, opts);
            String reason = m.get("reason") == null ? "" : String.valueOf(m.get("reason"));
            stages.add(new Stage(stages.size() + 1, opts, cost, checker.check(working).overall(), reason));
        }
        if (total.compareTo(budget) > 0) {
            return null;
        }
        return stages;
    }

    /** Fallback: split the value-ordered list into up to three stages. */
    List<Stage> valueStages(BikeBuild bike, List<Option> affordable) {
        int perStage = (int) Math.ceil(affordable.size() / (double) MAX_STAGES);
        String[] reasons = {"Biggest improvement for the money", "Next best value", "Finishing touches"};
        List<Stage> stages = new ArrayList<>();
        BikeBuild working = bike;
        for (int i = 0; i < affordable.size(); i += perStage) {
            List<Option> opts = affordable.subList(i, Math.min(affordable.size(), i + perStage));
            working = apply(working, opts);
            stages.add(new Stage(stages.size() + 1, List.copyOf(opts), sum(opts), checker.check(working).overall(),
                    reasons[Math.min(stages.size(), reasons.length - 1)]));
        }
        return stages;
    }

    private static BikeBuild apply(BikeBuild bike, List<Option> options) {
        BikeBuild working = bike;
        for (Option o : options) {
            for (Part p : o.parts()) {
                working = working.withPart(p);
            }
        }
        return working;
    }

    private static Money sum(List<Option> options) {
        Money total = zero();
        for (Option o : options) {
            total = total.add(o.cost());
        }
        return total;
    }

    private static Money zero() {
        return new Money(BigDecimal.ZERO, "CAD");
    }

    private static String facts(BikeBuild bike, RidingStyle style, Money budget, List<Option> options) {
        StringBuilder f = new StringBuilder("Rider style: " + style + "\nBudget: $" + budget.getAmount() + " CAD\nCurrent bike:");
        for (PartCategory slot : BikeBuild.REQUIRED_SLOTS) {
            Part p = bike.getPart(slot);
            f.append("\n- ").append(slot).append(": ").append(p == null ? "stock part" : p.getName());
        }
        f.append("\n\nUpgrade options (already checked; each is bought as a whole):");
        for (Option o : options) {
            f.append("\n").append(o.number()).append(". ").append(o.slots()).append(": ");
            o.parts().forEach(p -> f.append(p.getName()).append(" "));
            f.append("($").append(o.cost().getAmount()).append(") replacing ").append(o.replacing())
                    .append(". ").append(o.reason());
        }
        return f.toString();
    }
}
