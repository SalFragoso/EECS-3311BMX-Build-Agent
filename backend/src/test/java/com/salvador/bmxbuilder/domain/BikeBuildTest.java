package com.salvador.bmxbuilder.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BikeBuildTest {

    private Part part(String model, PartCategory category, String price, double grams) {
        return new Part("Test", model, category,
                new Money(new BigDecimal(price), "CAD"), grams, "test.png");
    }

    @Test
    void settingPartsUpdatesTotals() {
        BikeBuild build = new BikeBuild("Street build");
        build.setPart(PartCategory.FRAME, part("Frame", PartCategory.FRAME, "449.99", 2150));
        build.setPart(PartCategory.FORK, part("Fork", PartCategory.FORK, "179.99", 920));

        assertEquals(0, build.totalPrice().getAmount().compareTo(new BigDecimal("629.98")));
        assertEquals(3070, build.totalWeight());
    }

    @Test
    void swappingAPartReplacesTheOldOne() {
        BikeBuild build = new BikeBuild("Swap");
        build.setPart(PartCategory.FRAME, part("Old", PartCategory.FRAME, "300", 2400));
        Part newFrame = part("New", PartCategory.FRAME, "450", 2100);
        build.setPart(PartCategory.FRAME, newFrame);

        assertSame(newFrame, build.getPart(PartCategory.FRAME));
        assertEquals(0, build.totalPrice().getAmount().compareTo(new BigDecimal("450")));
    }

    @Test
    void partInTheWrongSlotIsRejected() {
        BikeBuild build = new BikeBuild("Wrong slot");
        Part fork = part("Fork", PartCategory.FORK, "179.99", 920);

        assertThrows(IllegalArgumentException.class, () -> build.setPart(PartCategory.FRAME, fork));
        assertFalse(build.hasPart(PartCategory.FRAME));
    }

    @Test
    void listenersHearAboutSetsAndRemoves() {
        BikeBuild build = new BikeBuild("Observed");
        List<BuildChangeEvent> events = new ArrayList<>();
        build.addListener(events::add);   // a listener that just records every event

        Part frame = part("Frame", PartCategory.FRAME, "449.99", 2150);
        build.setPart(PartCategory.FRAME, frame);
        build.removePart(PartCategory.FRAME);

        assertEquals(2, events.size());
        assertNull(events.get(0).oldPart());
        assertSame(frame, events.get(0).newPart());
        assertSame(frame, events.get(1).oldPart());
        assertNull(events.get(1).newPart());
    }

    @Test
    void removingAnEmptySlotNotifiesNobody() {
        BikeBuild build = new BikeBuild("Quiet");
        List<BuildChangeEvent> events = new ArrayList<>();
        build.addListener(events::add);

        build.removePart(PartCategory.SEAT);

        assertTrue(events.isEmpty());
    }

    @Test
    void buildIsCompleteOnlyWhenEveryRequiredSlotIsFilled() {
        BikeBuild build = new BikeBuild("Full");
        for (PartCategory slot : BikeBuild.REQUIRED_SLOTS) {
            assertFalse(build.isComplete());
            build.setPart(slot, part(slot.name(), slot, "10", 100));
        }

        assertTrue(build.isComplete());
        assertTrue(build.missingSlots().isEmpty());
        assertFalse(build.hasPart(PartCategory.BRAKES));   // brakes are optional
    }

    @Test
    void rootGroupsPartsIntoAssemblies() {
        BikeBuild build = new BikeBuild("Tree");
        build.setPart(PartCategory.FRAME, part("Frame", PartCategory.FRAME, "449.99", 2150));
        build.setPart(PartCategory.CRANKS, part("Cranks", PartCategory.CRANKS, "329.99", 900));
        build.setPart(PartCategory.SPROCKET, part("Sprocket", PartCategory.SPROCKET, "34.99", 110));

        List<BikeComponent> groups = build.getRoot().getChildren();

        assertEquals(2, groups.size());   // empty assemblies are left out
        assertEquals("Frame and fork", groups.get(0).getName());
        assertEquals("Drivetrain", groups.get(1).getName());
        assertEquals(2, groups.get(1).getParts().size());
    }

    @Test
    void withPartLeavesTheOriginalBuildAlone() {
        BikeBuild build = new BikeBuild("Original");
        BikeBuild copy = build.withPart(part("Frame", PartCategory.FRAME, "449.99", 2150));

        assertTrue(copy.hasPart(PartCategory.FRAME));
        assertFalse(build.hasPart(PartCategory.FRAME));
    }
}
