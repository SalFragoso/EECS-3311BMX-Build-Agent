package com.salvador.bmxbuilder.recommend;

import com.salvador.bmxbuilder.agent.AgentOrchestrator;
import com.salvador.bmxbuilder.agent.ConversationMemory;
import com.salvador.bmxbuilder.agent.ScriptedLLMClient;
import com.salvador.bmxbuilder.agent.knowledge.KnowledgeRetriever;
import com.salvador.bmxbuilder.agent.tools.ToolRegistry;
import com.salvador.bmxbuilder.domain.RidingStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StyleRecommenderTest {

    private ScriptedLLMClient llm;
    private StyleRecommender recommender;

    private final RiderProfile streetRider = new RiderProfile(175, "beginner", List.of("street"),
            "learn to grind ledges and rails", null, false);

    @BeforeEach
    void setUp() {
        llm = new ScriptedLLMClient();
        AgentOrchestrator agent = new AgentOrchestrator(llm, new ToolRegistry(List.of()), new ConversationMemory());
        recommender = new StyleRecommender(agent, new KnowledgeRetriever());
    }

    @Test
    void topTubeComesFromTheHeightTable() {
        assertEquals("20\" to 20.5\"", StyleRecommender.topTubeRange(160));
        assertEquals("20.5\" to 21\"", StyleRecommender.topTubeRange(175));
        assertEquals("21\" to 21.5\"", StyleRecommender.topTubeRange(190));
    }

    @Test
    void aiRankingIsUsedWhenItParses() {
        llm.thenAnswer("Here you go: {\"ranked\": [{\"style\": \"PARK\", \"score\": 60, \"reason\": \"ramps\"},"
                + " {\"style\": \"street\", \"score\": 90, \"reason\": \"you want to grind\"}]}");

        StyleRecommendation r = recommender.recommend(streetRider);

        assertTrue(r.aiBased());
        assertEquals(RidingStyle.STREET, r.best());     // sorted by score, case-insensitive style names
        assertEquals("you want to grind", r.ranked().get(0).reason());
        assertEquals("20.5\" to 21\"", r.topTube());
        assertTrue(llm.sentMessages.get(0).get(1).content().contains("learn to grind ledges"));
    }

    @Test
    void badOutputIsRetriedOnce() {
        llm.thenAnswer("I think street!").thenAnswer("{\"ranked\": [{\"style\": \"STREET\", \"score\": 80, \"reason\": \"ok\"}]}");

        StyleRecommendation r = recommender.recommend(streetRider);

        assertTrue(r.aiBased());
        assertEquals(2, llm.sentMessages.size());
    }

    @Test
    void twoBadAnswersFallBackToRules() {
        llm.thenAnswer("street").thenAnswer("{\"ranked\": \"nope\"}");

        StyleRecommendation r = recommender.recommend(streetRider);

        assertFalse(r.aiBased());
        assertTrue(r.note().startsWith("Basic recommendation"));
        assertEquals(RidingStyle.STREET, r.best());
    }

    @Test
    void aiDownFallsBackToRules() {
        llm.down();

        StyleRecommendation r = recommender.recommend(new RiderProfile(170, "INTERMEDIATE",
                List.of("track"), "go fast out of the gate", null, true));

        assertFalse(r.aiBased());
        assertEquals(RidingStyle.RACE, r.best());
        assertTrue(r.ranked().get(0).reason().contains("brakes"));
    }

    @Test
    void closeStylesAreFlagged() {
        List<StyleRecommendation.RankedStyle> ranked = StyleRecommender.ruleBased(
                new RiderProfile(175, "BEGINNER", List.of("street", "skatepark"), "", null, false));

        assertEquals(ranked.get(0).score(), ranked.get(1).score());   // street and park tie
        llm.down();
        assertTrue(recommender.recommend(new RiderProfile(175, "BEGINNER", List.of("street", "skatepark"), "", null, false)).close());
    }

    @Test
    void badHeightIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> recommender.recommend(new RiderProfile(40, "BEGINNER", List.of("street"), "", null, false)));
    }

    @Test
    void emptyQuizIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> recommender.recommend(new RiderProfile(175, "BEGINNER", List.of(), " ", null, false)));
    }

    @Test
    void unknownExperienceIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> recommender.recommend(new RiderProfile(175, "pro-ish", List.of("street"), "", null, false)));
    }
}
