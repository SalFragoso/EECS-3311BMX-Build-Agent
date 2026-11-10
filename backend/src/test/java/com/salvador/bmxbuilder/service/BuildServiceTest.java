package com.salvador.bmxbuilder.service;

import com.salvador.bmxbuilder.agent.AgentOrchestrator;
import com.salvador.bmxbuilder.agent.ConversationMemory;
import com.salvador.bmxbuilder.agent.ScriptedLLMClient;
import com.salvador.bmxbuilder.agent.knowledge.KnowledgeRetriever;
import com.salvador.bmxbuilder.agent.llm.ChatMessage;
import com.salvador.bmxbuilder.agent.llm.LLMClient;
import com.salvador.bmxbuilder.agent.llm.LLMException;
import com.salvador.bmxbuilder.agent.llm.LLMResponse;
import com.salvador.bmxbuilder.agent.llm.ToolSpec;
import com.salvador.bmxbuilder.agent.tools.AddPartToBuildTool;
import com.salvador.bmxbuilder.agent.tools.CheckCompatibilityTool;
import com.salvador.bmxbuilder.agent.tools.RetrieveKnowledgeTool;
import com.salvador.bmxbuilder.agent.tools.SearchCatalogTool;
import com.salvador.bmxbuilder.agent.tools.ToolRegistry;
import com.salvador.bmxbuilder.data.BuildRepository;
import com.salvador.bmxbuilder.data.PartRepository;
import com.salvador.bmxbuilder.domain.*;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityChecker;
import com.salvador.bmxbuilder.pricing.PriceService;
import com.salvador.bmxbuilder.recommend.BudgetOptimizer;
import com.salvador.bmxbuilder.recommend.StyleRecommender;
import com.salvador.bmxbuilder.domain.compatibility.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BuildServiceTest {

    private PartRepository partRepo;
    private BuildRepository buildRepo;
    private BuildService service;
    private ScriptedLLMClient llm;

    @BeforeEach
    void setUp() {
        // Fake repositories and a scripted fake LLM, so no database or API key is needed.
        // The checker, tools and agent loop are all the real ones.
        partRepo = mock(PartRepository.class);
        buildRepo = mock(BuildRepository.class);
        CompatibilityChecker checker = new CompatibilityChecker();
        llm = new ScriptedLLMClient();
        KnowledgeRetriever knowledge = new KnowledgeRetriever();
        ToolRegistry tools = new ToolRegistry(List.of(
                new SearchCatalogTool(partRepo),
                new AddPartToBuildTool(buildRepo, partRepo, checker),
                new CheckCompatibilityTool(buildRepo, checker),
                new RetrieveKnowledgeTool(knowledge)));
        AgentOrchestrator agent = new AgentOrchestrator(llm, tools, new ConversationMemory());
        service = new BuildService(partRepo, buildRepo, checker, agent, knowledge, new PriceService(List.of()),
                new BudgetOptimizer(checker), new StyleRecommender(agent, knowledge));

        // save() hands back whatever it was given, like the real repository does
        when(buildRepo.save(any(BikeBuild.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Entities normally get their id from the database; tests set it directly. */
    private static <T> T withId(T entity, long id) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Makes the fake build repository behave like a tiny database: save assigns ids, findById looks them up. */
    private Map<Long, BikeBuild> fakeBuildTable() {
        Map<Long, BikeBuild> table = new HashMap<>();
        when(buildRepo.save(any(BikeBuild.class))).thenAnswer(inv -> {
            BikeBuild b = inv.getArgument(0);
            if (b.getId() == null) {
                withId(b, table.size() + 1);
            }
            table.put(b.getId(), b);
            return b;
        });
        when(buildRepo.findById(any())).thenAnswer(inv -> Optional.ofNullable(table.get((Long) inv.getArgument(0))));
        when(buildRepo.findAll()).thenAnswer(inv -> new java.util.ArrayList<>(table.values()));
        return table;
    }

    private Part part(PartCategory category, Map<String, String> specs) {
        Part p = new Part("Test", category.name(), category,
                new Money(new BigDecimal("50"), "CAD"), 100, "test.png");
        p.getSpecs().putAll(specs);
        return p;
    }

    @Test
    void searchRejectsMinPriceAboveMaxPrice() {
        PartQuery q = new PartQuery(null, null, new BigDecimal("500"), new BigDecimal("100"), null);

        assertThrows(IllegalArgumentException.class, () -> service.searchParts(q));
        verify(partRepo, never()).search(any());
    }

    @Test
    void blankNameBecomesUntitled() {
        BuildView view = service.createBuild("   ");

        assertEquals("Untitled build", view.name());
    }

    @Test
    void selectPartSavesTheBuildAndReturnsTotals() {
        BikeBuild build = new BikeBuild("Street");
        Part frame = part(PartCategory.FRAME, Map.of("bbType", "MID"));
        when(buildRepo.findById(1L)).thenReturn(Optional.of(build));
        when(partRepo.findById(7L)).thenReturn(Optional.of(frame));

        BuildView view = service.selectPart(1L, PartCategory.FRAME, 7L);

        assertSame(frame, view.parts().get(PartCategory.FRAME));
        assertEquals(0, view.totalPrice().getAmount().compareTo(new BigDecimal("50")));
        verify(buildRepo).save(build);
    }

    @Test
    void selectPartFlagsAMisfitButStillAddsIt() {
        BikeBuild build = new BikeBuild("Mismatch");
        build.setPart(PartCategory.FRAME, part(PartCategory.FRAME, Map.of("bbType", "MID")));
        Part spanishBb = part(PartCategory.BOTTOM_BRACKET, Map.of("bbType", "SPANISH"));
        when(buildRepo.findById(1L)).thenReturn(Optional.of(build));
        when(partRepo.findById(8L)).thenReturn(Optional.of(spanishBb));

        BuildView view = service.selectPart(1L, PartCategory.BOTTOM_BRACKET, 8L);

        assertTrue(view.parts().containsKey(PartCategory.BOTTOM_BRACKET));
        assertEquals(Status.FAIL, view.compatibility().overall());
    }

    @Test
    void unknownBuildThrowsNotFound() {
        when(buildRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.selectPart(99L, PartCategory.FRAME, 1L));
    }

    @Test
    void partInTheWrongSlotIsRejectedAndNotSaved() {
        BikeBuild build = new BikeBuild("Wrong slot");
        when(buildRepo.findById(1L)).thenReturn(Optional.of(build));
        when(partRepo.findById(4L)).thenReturn(Optional.of(part(PartCategory.FORK, Map.of())));

        assertThrows(IllegalArgumentException.class, () -> service.selectPart(1L, PartCategory.FRAME, 4L));
        verify(buildRepo, never()).save(any());
    }

    @Test
    void compatiblePartsLeavesOutMisfits() {
        BikeBuild build = new BikeBuild("Mid frame");
        build.setPart(PartCategory.FRAME, part(PartCategory.FRAME, Map.of("bbType", "MID")));
        Part midBb = part(PartCategory.BOTTOM_BRACKET, Map.of("bbType", "MID"));
        Part spanishBb = part(PartCategory.BOTTOM_BRACKET, Map.of("bbType", "SPANISH"));
        when(buildRepo.findById(1L)).thenReturn(Optional.of(build));
        when(partRepo.findByCategory(PartCategory.BOTTOM_BRACKET)).thenReturn(List.of(midBb, spanishBb));

        List<Part> fits = service.compatibleParts(1L, PartCategory.BOTTOM_BRACKET);

        assertEquals(List.of(midBb), fits);
    }

    // ---------- F02: AI build planner ----------

    @Test
    void plannerFillsTheBuildThroughItsTools() {
        fakeBuildTable();
        Part frame = withId(part(PartCategory.FRAME, Map.of("bbType", "MID")), 1L);
        when(partRepo.findById(1L)).thenReturn(Optional.of(frame));
        llm.thenCall("add_part_to_build", Map.of("build_id", 1, "slot", "FRAME", "part_id", 1))
           .thenCall("check_compatibility", Map.of("build_id", 1))
           .thenAnswer("I picked a mid-BB frame for street.");

        PlanResult result = service.planBuild("street bike, no brakes", "Street");

        assertTrue(result.finished());
        assertTrue(result.aiAvailable());
        assertEquals("I picked a mid-BB frame for street.", result.summary());
        assertSame(frame, result.build().parts().get(PartCategory.FRAME));
        assertEquals("Street", result.build().name());
        assertEquals(2, result.steps().size());
        assertEquals(250, result.tokensUsed());
    }

    @Test
    void plannerFallsBackWhenTheAiIsDown() {
        fakeBuildTable();
        llm.down();

        PlanResult result = service.planBuild("street bike", null);

        assertFalse(result.aiAvailable());
        assertFalse(result.finished());
        assertTrue(result.summary().contains("unavailable"));
        assertEquals("AI build", result.build().name());   // the empty build is still there to fill manually
    }

    @Test
    void plannerKeepsPartialProgressIfTheAiFailsMidway() {
        fakeBuildTable();
        when(partRepo.findById(1L)).thenReturn(Optional.of(withId(part(PartCategory.FRAME, Map.of()), 1L)));
        FailingAfterOneCall llm2 = new FailingAfterOneCall(
                llm.thenCall("add_part_to_build", Map.of("build_id", 1, "slot", "FRAME", "part_id", 1)));
        BuildService flaky = new BuildService(partRepo, buildRepo, new CompatibilityChecker(),
                new AgentOrchestrator(llm2, new ToolRegistry(List.of(new AddPartToBuildTool(buildRepo, partRepo,
                        new CompatibilityChecker()))), new ConversationMemory()), new KnowledgeRetriever(),
                new PriceService(List.of()), new BudgetOptimizer(new CompatibilityChecker()), null);

        PlanResult result = flaky.planBuild("street bike", null);

        assertFalse(result.aiAvailable());
        assertTrue(result.summary().contains("stopped partway"));
        assertTrue(result.build().parts().containsKey(PartCategory.FRAME));
    }

    /** Answers once from the scripted LLM, then fails like a rate limit would. */
    private static class FailingAfterOneCall implements LLMClient {
        private final ScriptedLLMClient first;
        private int calls;

        FailingAfterOneCall(ScriptedLLMClient first) {
            this.first = first;
        }

        @Override
        public LLMResponse complete(List<ChatMessage> messages, List<ToolSpec> tools) {
            if (calls++ > 0) {
                throw new LLMException("Groq rate limit reached.");
            }
            return first.complete(messages, tools);
        }

        @Override
        public String modelName() {
            return "flaky";
        }
    }

    @Test
    void plannerNeedsARequest() {
        assertThrows(IllegalArgumentException.class, () -> service.planBuild("  ", null));
    }

    // ---------- F09: BMX Q&A assistant ----------

    @Test
    void questionIsSentWithMatchingArticles() {
        llm.thenAnswer("Mid is the most common.");

        AgentAnswer answer = service.askQuestion("t1", "mid or spanish bottom bracket?", null);

        assertTrue(answer.aiAvailable());
        assertEquals("Mid is the most common.", answer.answer());
        assertEquals("Bottom bracket types: Mid, Spanish and American", answer.sources().get(0));
        String sentToModel = llm.sentMessages.get(0).get(1).content();
        assertTrue(sentToModel.contains("Knowledge base passages"));
        assertTrue(sentToModel.contains("Spanish BBs press bearings"));
    }

    @Test
    void agentCanSearchTheKnowledgeBaseItself() {
        llm.thenCall("search_knowledge", Map.of("query", "freecoaster")).thenAnswer("It rolls backwards freely.");

        AgentAnswer answer = service.askQuestion("t2", "what hub for fakies?", null);

        assertEquals(1, answer.steps().size());
        assertTrue(answer.steps().get(0).endsWith("-> ok"));
    }

    @Test
    void currentBuildIsIncludedWhenGiven() {
        Map<Long, BikeBuild> table = fakeBuildTable();
        BikeBuild build = buildRepo.save(new BikeBuild("Mine"));
        build.setPart(PartCategory.FRAME, withId(part(PartCategory.FRAME, Map.of("bbType", "MID")), 1L));
        llm.thenAnswer("Your frame takes a Mid BB.");

        service.askQuestion("t3", "which bottom bracket fits my frame?", build.getId());

        assertTrue(llm.sentMessages.get(0).get(1).content().contains("The rider's current build"));
        assertEquals(1, table.size());
    }

    @Test
    void followUpQuestionsRememberTheConversation() {
        llm.thenAnswer("A freecoaster lets you roll backwards.").thenAnswer("Yes, for street it's popular.");

        service.askQuestion("t4", "what's a freecoaster?", null);
        service.askQuestion("t4", "is it good for street?", null);

        // system, earlier question, earlier answer, new question
        assertEquals(4, llm.sentMessages.get(1).size());
    }

    @Test
    void whenTheAiIsDownMatchingArticlesAreShown() {
        llm.down();

        AgentAnswer answer = service.askQuestion("t5", "what's a freecoaster?", null);

        assertFalse(answer.aiAvailable());
        assertTrue(answer.answer().contains("unavailable"));
        assertTrue(answer.answer().contains("Freecoaster vs cassette"));
    }

    @Test
    void blankQuestionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.askQuestion("t6", " ", null));
    }

    // ---------- F10: saved builds and comparison ----------

    @Test
    void compareLinesUpSlotsAndFlagsDifferences() {
        fakeBuildTable();
        Part frame = withId(part(PartCategory.FRAME, Map.of()), 1L);
        Part forkA = withId(part(PartCategory.FORK, Map.of()), 2L);
        Part forkB = withId(part(PartCategory.FORK, Map.of()), 3L);
        BikeBuild a = buildRepo.save(new BikeBuild("A"));
        BikeBuild b = buildRepo.save(new BikeBuild("B"));
        a.setPart(PartCategory.FRAME, frame);
        b.setPart(PartCategory.FRAME, frame);
        a.setPart(PartCategory.FORK, forkA);
        b.setPart(PartCategory.FORK, forkB);

        BuildComparison c = service.compareBuilds(List.of(a.getId(), b.getId()));

        assertEquals(2, c.builds().size());
        assertEquals(2, c.rows().size());   // only slots that some build uses
        assertFalse(c.rows().get(0).differs());   // same frame
        assertTrue(c.rows().get(1).differs());    // different forks
    }

    @Test
    void compareNeedsTwoOrThreeBuilds() {
        assertThrows(IllegalArgumentException.class, () -> service.compareBuilds(List.of(1L)));
        assertThrows(IllegalArgumentException.class, () -> service.compareBuilds(List.of(1L, 2L, 3L, 4L)));
    }

    @Test
    void duplicateNamesAreRejected() {
        fakeBuildTable();
        buildRepo.save(new BikeBuild("Street"));
        BikeBuild other = buildRepo.save(new BikeBuild("Park"));

        assertThrows(IllegalArgumentException.class, () -> service.renameBuild(other.getId(), "street"));
        assertEquals("Park v2", service.renameBuild(other.getId(), "Park v2").name());
    }

    @Test
    void duplicateCopiesTheParts() {
        fakeBuildTable();
        BikeBuild original = buildRepo.save(new BikeBuild("Original"));
        original.setPart(PartCategory.FRAME, withId(part(PartCategory.FRAME, Map.of()), 1L));

        BuildView copy = service.duplicateBuild(original.getId());

        assertEquals("Original (copy)", copy.name());
        assertTrue(copy.parts().containsKey(PartCategory.FRAME));
        assertNotEquals(original.getId(), copy.id());
    }

    @Test
    void deletingAMissingBuildIsNotFound() {
        fakeBuildTable();

        assertThrows(NotFoundException.class, () -> service.deleteBuild(42L));
    }

    // ---------- F07: budget optimizer ----------

    @Test
    void optimizerSwapsAreExplainedByTheAgent() {
        fakeBuildTable();
        Part bars = withId(part(PartCategory.BARS, Map.of()), 1L);   // $50
        Part cheapBars = withId(new Part("Test", "Cheap bars", PartCategory.BARS,
                new Money(new BigDecimal("20"), "CAD"), 900, "x.png"), 2L);
        BikeBuild build = buildRepo.save(new BikeBuild("Budget"));
        build.setPart(PartCategory.BARS, bars);
        when(partRepo.findAll()).thenReturn(List.of(bars, cheapBars));
        llm.thenAnswer("- Cheaper bars, same feel.");

        OptimizationResult result = service.optimizeBudget(build.getId(), new BigDecimal("30"), List.of(), RidingStyle.STREET);

        assertTrue(result.plan().reachable());
        assertTrue(result.aiExplained());
        assertEquals("- Cheaper bars, same feel.", result.explanation());
        assertTrue(llm.sentMessages.get(0).get(1).content().contains("Cheap bars"));   // the agent saw the facts
        assertSame(bars, build.getPart(PartCategory.BARS));   // nothing applied yet
    }

    @Test
    void optimizerStillWorksWhenTheAiIsDown() {
        fakeBuildTable();
        Part bars = withId(part(PartCategory.BARS, Map.of()), 1L);
        Part cheapBars = withId(new Part("Test", "Cheap bars", PartCategory.BARS,
                new Money(new BigDecimal("20"), "CAD"), 900, "x.png"), 2L);
        BikeBuild build = buildRepo.save(new BikeBuild("Budget"));
        build.setPart(PartCategory.BARS, bars);
        when(partRepo.findAll()).thenReturn(List.of(bars, cheapBars));
        llm.down();

        OptimizationResult result = service.optimizeBudget(build.getId(), new BigDecimal("30"), null, null);

        assertFalse(result.aiExplained());
        assertEquals(1, result.plan().swaps().size());
    }

    @Test
    void applyingSwapsUpdatesTheBuild() {
        fakeBuildTable();
        BikeBuild build = buildRepo.save(new BikeBuild("Budget"));
        build.setPart(PartCategory.BARS, withId(part(PartCategory.BARS, Map.of()), 1L));
        Part cheapBars = withId(part(PartCategory.BARS, Map.of()), 2L);
        when(partRepo.findById(2L)).thenReturn(Optional.of(cheapBars));

        BuildView view = service.applySwaps(build.getId(), Map.of(PartCategory.BARS, 2L));

        assertSame(cheapBars, view.parts().get(PartCategory.BARS));
    }
}
