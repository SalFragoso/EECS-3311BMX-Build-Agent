package com.salvador.bmxbuilder.agent.tools;

import com.salvador.bmxbuilder.agent.llm.ToolCall;
import com.salvador.bmxbuilder.data.BuildRepository;
import com.salvador.bmxbuilder.data.PartRepository;
import com.salvador.bmxbuilder.domain.*;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentToolsTest {

    private PartRepository partRepo;
    private BuildRepository buildRepo;
    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        partRepo = mock(PartRepository.class);
        buildRepo = mock(BuildRepository.class);
        when(buildRepo.save(any(BikeBuild.class))).thenAnswer(inv -> inv.getArgument(0));
        CompatibilityChecker checker = new CompatibilityChecker();
        registry = new ToolRegistry(List.of(
                new SearchCatalogTool(partRepo),
                new AddPartToBuildTool(buildRepo, partRepo, checker),
                new CheckCompatibilityTool(buildRepo, checker)));
    }

    /** Parts normally get their id from the database; tests set it directly. */
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

    private Part part(long id, PartCategory category, String price, Map<String, String> specs) {
        Part p = new Part("Test", category.name(), category, new Money(new BigDecimal(price), "CAD"), 100, "x.png");
        p.getSpecs().putAll(specs);
        return withId(p, id);
    }

    private ToolCall call(String name, Map<String, Object> args) {
        return new ToolCall("call_1", name, args, "{}");
    }

    @Test
    void registryListsEveryToolSpec() {
        assertEquals(3, registry.specs().size());
        assertEquals(1, registry.specs(List.of("search_catalog")).size());
    }

    @Test
    void unknownToolIsAnErrorNotACrash() {
        ToolResult r = registry.execute(call("teleport", Map.of()));

        assertFalse(r.success());
        assertTrue(r.toMessageContent().startsWith("ERROR: Unknown tool"));
    }

    @Test
    void searchPassesFiltersThrough() {
        when(partRepo.search(any())).thenReturn(List.of(part(1, PartCategory.FRAME, "449.99", Map.of("bbType", "MID"))));

        ToolResult r = registry.execute(call("search_catalog", Map.of("category", "frame", "max_price", 450)));

        assertTrue(r.success());
        assertTrue(r.content().contains("id=1"));
        assertTrue(r.content().contains("bbType=MID"));
    }

    @Test
    void searchRejectsABadCategory() {
        ToolResult r = registry.execute(call("search_catalog", Map.of("category", "rocket")));

        assertFalse(r.success());
        assertTrue(r.content().contains("'rocket' isn't a valid category"));
    }

    @Test
    void searchWithNoMatchesSaysSo() {
        when(partRepo.search(any())).thenReturn(List.of());

        assertTrue(registry.execute(call("search_catalog", Map.of())).content().startsWith("No parts matched"));
    }

    @Test
    void addPartFillsTheSlotAndReportsCompatibility() {
        BikeBuild build = withId(new BikeBuild("Agent build"), 5);
        build.setPart(PartCategory.FRAME, part(1, PartCategory.FRAME, "449.99", Map.of("bbType", "MID")));
        when(buildRepo.findById(5L)).thenReturn(Optional.of(build));
        when(partRepo.findById(8L)).thenReturn(Optional.of(
                part(8, PartCategory.BOTTOM_BRACKET, "44.99", Map.of("bbType", "SPANISH"))));

        // numbers as strings on purpose: models sometimes send them that way
        ToolResult r = registry.execute(call("add_part_to_build",
                Map.of("build_id", "5", "slot", "BOTTOM_BRACKET", "part_id", 8)));

        assertTrue(r.success());
        assertTrue(build.hasPart(PartCategory.BOTTOM_BRACKET));
        assertTrue(r.content().contains("Compatibility: FAIL"));   // the agent gets told so it can fix it
        verify(buildRepo).save(build);
    }

    @Test
    void addingAMadeUpPartIdTellsTheAgentToSearch() {
        when(buildRepo.findById(5L)).thenReturn(Optional.of(withId(new BikeBuild("b"), 5)));
        when(partRepo.findById(999L)).thenReturn(Optional.empty());

        ToolResult r = registry.execute(call("add_part_to_build",
                Map.of("build_id", 5, "slot", "FRAME", "part_id", 999)));

        assertFalse(r.success());
        assertTrue(r.content().contains("search_catalog"));
    }

    @Test
    void addingToTheWrongSlotIsAnError() {
        when(buildRepo.findById(5L)).thenReturn(Optional.of(withId(new BikeBuild("b"), 5)));
        when(partRepo.findById(4L)).thenReturn(Optional.of(part(4, PartCategory.FORK, "179.99", Map.of())));

        ToolResult r = registry.execute(call("add_part_to_build", Map.of("build_id", 5, "slot", "FRAME", "part_id", 4)));

        assertFalse(r.success());
        verify(buildRepo, never()).save(any());
    }

    @Test
    void missingArgumentIsNamed() {
        ToolResult r = registry.execute(call("check_compatibility", Map.of()));

        assertFalse(r.success());
        assertTrue(r.content().contains("'build_id'"));
    }

    @Test
    void checkShowsMissingSlots() {
        when(buildRepo.findById(2L)).thenReturn(Optional.of(withId(new BikeBuild("Empty"), 2)));

        ToolResult r = registry.execute(call("check_compatibility", Map.of("build_id", 2)));

        assertTrue(r.content().contains("Missing required slots"));
        assertTrue(r.content().contains("Parts: none yet"));
    }
}
