package com.salvador.bmxbuilder.service;

import com.salvador.bmxbuilder.agent.AgentOrchestrator;
import com.salvador.bmxbuilder.agent.ConversationMemory;
import com.salvador.bmxbuilder.agent.ScriptedLLMClient;
import com.salvador.bmxbuilder.agent.tools.AddPartToBuildTool;
import com.salvador.bmxbuilder.agent.tools.CheckCompatibilityTool;
import com.salvador.bmxbuilder.agent.tools.SearchCatalogTool;
import com.salvador.bmxbuilder.agent.tools.ToolRegistry;
import com.salvador.bmxbuilder.data.BuildRepository;
import com.salvador.bmxbuilder.data.PartRepository;
import com.salvador.bmxbuilder.domain.*;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityChecker;
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
        ToolRegistry tools = new ToolRegistry(List.of(
                new SearchCatalogTool(partRepo),
                new AddPartToBuildTool(buildRepo, partRepo, checker),
                new CheckCompatibilityTool(buildRepo, checker)));
        AgentOrchestrator agent = new AgentOrchestrator(llm, tools, new ConversationMemory());
        service = new BuildService(partRepo, buildRepo, checker, agent);

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
    void plannerNeedsARequest() {
        assertThrows(IllegalArgumentException.class, () -> service.planBuild("  ", null));
    }
}
