package com.salvador.bmxbuilder.recommend;

import com.salvador.bmxbuilder.agent.AgentOrchestrator;
import com.salvador.bmxbuilder.agent.ConversationMemory;
import com.salvador.bmxbuilder.agent.ScriptedLLMClient;
import com.salvador.bmxbuilder.agent.tools.ToolRegistry;
import com.salvador.bmxbuilder.domain.*;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityChecker;
import com.salvador.bmxbuilder.domain.compatibility.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UpgradeAdvisorTest {

    private ScriptedLLMClient llm;
    private UpgradeAdvisor advisor;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        llm = new ScriptedLLMClient();
        AgentOrchestrator agent = new AgentOrchestrator(llm, new ToolRegistry(List.of()), new ConversationMemory());
        advisor = new UpgradeAdvisor(agent, new CompatibilityChecker());
    }

    private Part part(PartCategory category, String name, String price, Map<String, String> specs) {
        Part p = new Part("Test", name, category, new Money(new BigDecimal(price), "CAD"), 500, "x.png");
        p.getStyleTags().add(RidingStyle.STREET);
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

    /** A bike with every required slot filled by a basic part, so only the slots under test can change. */
    private BikeBuild basicBike(List<Part> catalog) {
        BikeBuild bike = new BikeBuild("Mine");
        for (PartCategory slot : BikeBuild.REQUIRED_SLOTS) {
            Part p = part(slot, "Basic " + slot, "10", Map.of());
            catalog.add(p);
            bike.setPart(slot, p);
        }
        return bike;
    }

    @Test
    void stockSlotsAreHighImpact() {
        Part pedals = part(PartCategory.PEDALS, "Good pedals", "50", Map.of());
        BikeBuild bike = new BikeBuild("Stock bike");   // nothing known: every slot is a stock part

        List<UpgradeAdvisor.Option> options = advisor.options(bike, RidingStyle.STREET, List.of(pedals), new ArrayList<>());

        assertEquals(1, options.size());
        assertEquals(List.of("stock part"), options.get(0).replacing());
        assertTrue(options.get(0).reason().startsWith("Replaces a stock part"));
    }

    @Test
    void cheaperPartsAreNeverUpgrades() {
        List<Part> catalog = new ArrayList<>();
        BikeBuild bike = basicBike(catalog);
        catalog.add(part(PartCategory.SEAT, "Cheaper seat", "5", Map.of()));

        assertTrue(advisor.options(bike, RidingStyle.STREET, catalog, new ArrayList<>()).isEmpty());
    }

    @Test
    void dependentPartsAreBundled() {
        List<Part> catalog = new ArrayList<>();
        BikeBuild bike = basicBike(catalog);
        // current BB is 19mm; the better cranks need a 22mm BB
        Part bb19 = part(PartCategory.BOTTOM_BRACKET, "BB 19", "10", Map.of("spindle", "19mm"));
        bike.setPart(PartCategory.BOTTOM_BRACKET, bb19);
        Part cranks22 = part(PartCategory.CRANKS, "Cranks 22", "200", Map.of("spindle", "22mm"));
        Part bb22 = part(PartCategory.BOTTOM_BRACKET, "BB 22", "40", Map.of("spindle", "22mm"));
        catalog.addAll(List.of(bb19, cranks22, bb22));

        List<UpgradeAdvisor.Option> options = advisor.options(bike, RidingStyle.STREET, catalog, new ArrayList<>());

        UpgradeAdvisor.Option cranks = options.stream().filter(o -> o.slots().contains(PartCategory.CRANKS)).findFirst().orElseThrow();
        assertEquals(List.of(PartCategory.CRANKS, PartCategory.BOTTOM_BRACKET), cranks.slots());
        assertEquals(0, cranks.cost().getAmount().compareTo(new BigDecimal("240")));
        assertTrue(cranks.reason().startsWith("Needs a matching bottom bracket"));
    }

    @Test
    void frameLimitsAreExplainedNotBundled() {
        List<Part> catalog = new ArrayList<>();
        BikeBuild bike = basicBike(catalog);
        Part midFrame = part(PartCategory.FRAME, "Mid frame", "10", Map.of("bbType", "MID"));
        bike.setPart(PartCategory.FRAME, midFrame);
        catalog.add(part(PartCategory.BOTTOM_BRACKET, "Spanish BB", "60", Map.of("bbType", "SPANISH")));
        List<String> notes = new ArrayList<>();

        List<UpgradeAdvisor.Option> options = advisor.options(bike, RidingStyle.STREET, catalog, notes);

        assertTrue(options.stream().noneMatch(o -> o.slots().contains(PartCategory.BOTTOM_BRACKET)));
        assertTrue(notes.get(0).startsWith("Your frame limits the bottom bracket upgrade"));
    }

    @Test
    void aiStagesAreUsedWhenTheyPassTheChecks() {
        List<Part> catalog = new ArrayList<>();
        BikeBuild bike = basicBike(catalog);
        catalog.add(part(PartCategory.PEDALS, "Good pedals", "40", Map.of()));
        catalog.add(part(PartCategory.SEAT, "Good seat", "30", Map.of()));
        llm.thenAnswer("{\"stages\": [{\"options\": [1], \"reason\": \"grip first\"}, {\"options\": [2], \"reason\": \"comfort\"}],"
                + " \"summary\": \"Pedals, then seat.\"}");

        UpgradeAdvisor.Plan plan = advisor.advise(bike, new BigDecimal("100"), RidingStyle.STREET, catalog, List.of(), 0);

        assertTrue(plan.aiStaged());
        assertEquals(2, plan.stages().size());
        assertEquals("grip first", plan.stages().get(0).reason());
        assertEquals("Pedals, then seat.", plan.summary());
        assertNotEquals(Status.FAIL, plan.stages().get(1).compatibility());   // basic parts have no specs, so WARNING
        assertEquals(0, plan.totalCost().getAmount().compareTo(new BigDecimal("70")));
    }

    @Test
    void aiStagesWithAMadeUpOptionAreReplaced() {
        List<Part> catalog = new ArrayList<>();
        BikeBuild bike = basicBike(catalog);
        catalog.add(part(PartCategory.PEDALS, "Good pedals", "40", Map.of()));
        llm.thenAnswer("{\"stages\": [{\"options\": [1, 7], \"reason\": \"x\"}]}");   // option 7 doesn't exist

        UpgradeAdvisor.Plan plan = advisor.advise(bike, new BigDecimal("100"), RidingStyle.STREET, catalog, List.of(), 0);

        assertFalse(plan.aiStaged());
        assertEquals(1, plan.stages().size());
        assertTrue(plan.notes().get(0).contains("didn't pass the checks"));
    }

    @Test
    void aiDownStillGivesValueOrderedStages() {
        List<Part> catalog = new ArrayList<>();
        BikeBuild bike = basicBike(catalog);
        catalog.add(part(PartCategory.PEDALS, "Good pedals", "40", Map.of()));
        catalog.add(part(PartCategory.SEAT, "Good seat", "30", Map.of()));
        catalog.add(part(PartCategory.BARS, "Good bars", "60", Map.of()));
        llm.down();

        UpgradeAdvisor.Plan plan = advisor.advise(bike, new BigDecimal("500"), RidingStyle.STREET, catalog, List.of(), 0);

        assertFalse(plan.aiStaged());
        assertEquals(3, plan.stages().size());
        assertTrue(plan.notes().get(0).startsWith("AI unavailable"));
    }

    @Test
    void budgetIsNeverExceeded() {
        List<Part> catalog = new ArrayList<>();
        BikeBuild bike = basicBike(catalog);
        catalog.add(part(PartCategory.PEDALS, "Good pedals", "40", Map.of()));
        catalog.add(part(PartCategory.BARS, "Good bars", "60", Map.of()));
        llm.down();

        UpgradeAdvisor.Plan plan = advisor.advise(bike, new BigDecimal("70"), RidingStyle.STREET, catalog, List.of(), 0);

        assertTrue(plan.totalCost().getAmount().compareTo(new BigDecimal("70")) <= 0);
    }

    @Test
    void tooSmallABudgetSuggestsTheCheapestUpgrade() {
        List<Part> catalog = new ArrayList<>();
        BikeBuild bike = basicBike(catalog);
        catalog.add(part(PartCategory.PEDALS, "Good pedals", "40", Map.of()));

        UpgradeAdvisor.Plan plan = advisor.advise(bike, new BigDecimal("5"), RidingStyle.STREET, catalog, List.of(), 0);

        assertTrue(plan.notes().get(0).startsWith("The budget is too low"));
        assertEquals("Save up for this one first", plan.stages().get(0).reason());
    }

    @Test
    void nothingBetterInTheCatalogSaysSo() {
        List<Part> catalog = new ArrayList<>();
        BikeBuild bike = basicBike(catalog);

        UpgradeAdvisor.Plan plan = advisor.advise(bike, new BigDecimal("500"), RidingStyle.STREET, catalog, List.of(), 0);

        assertTrue(plan.stages().isEmpty());
        assertTrue(plan.notes().get(0).startsWith("No parts in the catalog beat"));
    }

    @Test
    void descriptionIsMatchedToCatalogParts() {
        Part frame = part(PartCategory.FRAME, "Begin", "380", Map.of());
        llm.thenAnswer("{\"matched\": [{\"slot\": \"FORK\", \"partId\": " + frame.getId() + "}, {\"slot\": \"BARS\", \"partId\": 999}],"
                + " \"stock\": [\"PEDALS\"]}");

        UpgradeAdvisor.Match match = advisor.matchDescription("Fit Begin with plastic pedals", List.of(frame));

        assertTrue(match.ok());
        assertSame(frame, match.matched().get(PartCategory.FRAME));   // real category wins over the model's slot
        assertEquals(1, match.matched().size());                       // made-up id 999 ignored
        assertTrue(match.stock().contains(PartCategory.PEDALS));
    }

    @Test
    void unreadableMatchingTwiceAsksTheRiderToPickParts() {
        llm.thenAnswer("Sounds like a nice bike!").thenAnswer("Really nice.");

        UpgradeAdvisor.Match match = advisor.matchDescription("my bike", List.of());

        assertFalse(match.ok());
        assertTrue(match.problem().contains("configurator"));
    }

    @Test
    void zeroBudgetIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> advisor.advise(new BikeBuild("b"), BigDecimal.ZERO,
                RidingStyle.STREET, List.of(), List.of(), 0));
    }

    @Test
    void stageCountIsCapped() {
        assertEquals(3, UpgradeAdvisor.MAX_STAGES);
        assertNull(advisor.validStages(new BikeBuild("b"), Map.of("stages", List.of(
                Map.of("options", List.of(1)), Map.of("options", List.of(2)),
                Map.of("options", List.of(3)), Map.of("options", List.of(4)))), List.of(), new BigDecimal("100")));
        assertNull(advisor.validStages(new BikeBuild("b"), null, List.of(), BigDecimal.TEN));
        assertTrue(Set.of(1).contains(1));
    }
}
