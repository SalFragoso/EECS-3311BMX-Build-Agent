package com.salvador.bmxbuilder.recommend;

import com.salvador.bmxbuilder.agent.AgentOrchestrator;
import com.salvador.bmxbuilder.agent.AgentResult;
import com.salvador.bmxbuilder.agent.AgentTask;
import com.salvador.bmxbuilder.agent.Prompts;
import com.salvador.bmxbuilder.agent.knowledge.KnowledgeDoc;
import com.salvador.bmxbuilder.agent.knowledge.KnowledgeRetriever;
import com.salvador.bmxbuilder.agent.llm.LLMException;
import com.salvador.bmxbuilder.domain.RidingStyle;
import com.salvador.bmxbuilder.recommend.StyleRecommendation.RankedStyle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * F01, hybrid: the frame size comes from a height table (deterministic) and the style ranking comes
 * from the LLM. If the LLM is down, or its answer can't be parsed twice in a row, a rule-based score
 * is used and labelled as a basic recommendation.
 */
@Component
public class StyleRecommender {

    static final int CLOSE_GAP = 10;

    private final AgentOrchestrator agent;
    private final KnowledgeRetriever knowledge;
    private final JsonMapper json = JsonMapper.builder().build();

    public StyleRecommender(AgentOrchestrator agent, KnowledgeRetriever knowledge) {
        this.agent = agent;
        this.knowledge = knowledge;
    }

    public StyleRecommendation recommend(RiderProfile profile) {
        profile.validate();
        String topTube = topTubeRange(profile.heightCm());
        String facts = quizFacts(profile);
        int tokens = 0;

        try {
            for (int attempt = 0; attempt < 2; attempt++) {   // bad output gets one retry (F01)
                AgentResult result = agent.run(new AgentTask("style-" + UUID.randomUUID(),
                        Prompts.styleRecommender(), "Recommend my riding style.", facts, Set.of(), 2));
                tokens += result.tokensUsed();
                List<RankedStyle> ranked = parseRanking(result.text());
                if (!ranked.isEmpty()) {
                    return new StyleRecommendation(ranked, topTube, true, isClose(ranked), null, tokens);
                }
            }
            return basic(profile, topTube, "Basic recommendation: the AI's answer couldn't be read.", tokens);
        } catch (LLMException e) {
            return basic(profile, topTube, "Basic recommendation: AI unavailable (" + e.getMessage() + ").", tokens);
        }
    }

    // ---------- deterministic parts ----------

    /** Rough top tube guide from the knowledge base article (kb-top-tube). */
    public static String topTubeRange(int heightCm) {
        if (heightCm < 168) {          // under about 5'6"
            return "20\" to 20.5\"";
        }
        if (heightCm <= 183) {         // about 5'6" to 6'
            return "20.5\" to 21\"";
        }
        return "21\" to 21.5\"";
    }

    /** Points per style from where they ride, what they want to learn and brakes. */
    public static List<RankedStyle> ruleBased(RiderProfile profile) {
        Map<RidingStyle, Integer> points = new EnumMap<>(RidingStyle.class);
        Map<RidingStyle, List<String>> why = new EnumMap<>(RidingStyle.class);
        for (RidingStyle s : RidingStyle.values()) {
            points.put(s, 0);
            why.put(s, new ArrayList<>());
        }

        String spots = String.join(" ", profile.spots()).toLowerCase();
        add(points, why, RidingStyle.STREET, spots, 3, "you ride street", "street", "city", "ledge", "stairs");
        add(points, why, RidingStyle.PARK, spots, 3, "you ride skateparks", "park", "skatepark", "bowl", "ramp");
        add(points, why, RidingStyle.DIRT, spots, 3, "you ride dirt", "dirt", "trail", "jumps");
        add(points, why, RidingStyle.RACE, spots, 3, "you ride a track", "track", "race");
        add(points, why, RidingStyle.FLATLAND, spots, 3, "you ride flat ground", "flat", "parking lot");

        String goals = profile.goals().toLowerCase();
        add(points, why, RidingStyle.STREET, goals, 2, "your goals are street tricks", "grind", "rail", "ledge", "wallride", "manual");
        add(points, why, RidingStyle.PARK, goals, 2, "your goals suit ramps", "air", "bowl", "transfer", "quarter", "180", "360");
        add(points, why, RidingStyle.DIRT, goals, 2, "your goals are jumping", "jump", "trail", "flow", "whip");
        add(points, why, RidingStyle.RACE, goals, 2, "your goals are racing", "race", "speed", "gate", "win", "fast");
        add(points, why, RidingStyle.FLATLAND, goals, 2, "your goals are flatland tricks", "flatland", "balance", "spin", "pegs");

        if (profile.wantsBrakes()) {
            points.merge(RidingStyle.RACE, 1, Integer::sum);
            why.get(RidingStyle.RACE).add("you want brakes, which race bikes always have");
        }

        int max = Math.max(1, points.values().stream().mapToInt(Integer::intValue).max().orElse(1));
        return points.entrySet().stream()
                .sorted(Map.Entry.<RidingStyle, Integer>comparingByValue().reversed())
                .map(e -> new RankedStyle(e.getKey(), e.getValue() * 100 / max,
                        why.get(e.getKey()).isEmpty() ? "Nothing in your answers points here."
                                : "Because " + String.join(" and ", why.get(e.getKey())) + "."))
                .toList();
    }

    private static void add(Map<RidingStyle, Integer> points, Map<RidingStyle, List<String>> why,
                            RidingStyle style, String text, int weight, String reason, String... words) {
        for (String w : words) {
            if (text.contains(w)) {
                points.merge(style, weight, Integer::sum);
                if (!why.get(style).contains(reason)) {
                    why.get(style).add(reason);
                }
                return;
            }
        }
    }

    private StyleRecommendation basic(RiderProfile profile, String topTube, String note, int tokens) {
        List<RankedStyle> ranked = ruleBased(profile);
        return new StyleRecommendation(ranked, topTube, false, isClose(ranked), note, tokens);
    }

    private static boolean isClose(List<RankedStyle> ranked) {
        return ranked.size() > 1 && ranked.get(0).score() - ranked.get(1).score() <= CLOSE_GAP;
    }

    private String quizFacts(RiderProfile p) {
        StringBuilder facts = new StringBuilder("Quiz answers:\n");
        facts.append("- Height: ").append(p.heightCm()).append(" cm\n");
        facts.append("- Experience: ").append(p.experience()).append("\n");
        facts.append("- Where they ride: ").append(p.spots().isEmpty() ? "not given" : String.join(", ", p.spots())).append("\n");
        facts.append("- Goals: ").append(p.goals().isBlank() ? "not given" : p.goals()).append("\n");
        facts.append("- Budget: ").append(p.budget() == null ? "not given" : "$" + p.budget() + " CAD").append("\n");
        facts.append("- Wants brakes: ").append(p.wantsBrakes() ? "yes" : "no").append("\n");
        List<KnowledgeDoc> styles = knowledge.retrieve("riding styles street park dirt flatland race", 1);
        if (!styles.isEmpty()) {
            facts.append("\nStyle descriptions:\n").append(styles.get(0).text());
        }
        return facts.toString();
    }

    /** Pulls the JSON object out of the model's reply. Empty list if it isn't usable. */
    @SuppressWarnings("unchecked")
    List<RankedStyle> parseRanking(String text) {
        if (text == null) {
            return List.of();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return List.of();
        }
        try {
            Map<String, Object> root = json.readValue(text.substring(start, end + 1), Map.class);
            Object rankedObj = root.get("ranked");
            if (!(rankedObj instanceof List<?> list)) {
                return List.of();
            }
            List<RankedStyle> out = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) {
                    continue;
                }
                try {
                    RidingStyle style = RidingStyle.valueOf(String.valueOf(m.get("style")).trim().toUpperCase());
                    int score = (m.get("score") instanceof Number n) ? Math.max(0, Math.min(100, n.intValue())) : 0;
                    String reason = m.get("reason") == null ? "" : String.valueOf(m.get("reason"));
                    if (out.stream().noneMatch(r -> r.style() == style)) {
                        out.add(new RankedStyle(style, score, reason));
                    }
                } catch (IllegalArgumentException unknownStyle) {
                    // skip styles we don't have
                }
            }
            out.sort(Comparator.comparingInt(RankedStyle::score).reversed());
            return out;
        } catch (RuntimeException badJson) {
            return List.of();
        }
    }
}
