package com.salvador.bmxbuilder.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AssemblyTest {

    private Part part(String model, PartCategory category, String price, double grams) {
        return new Part("Test", model, category,
                new Money(new BigDecimal(price), "CAD"), grams, "test.png");
    }

    @Test
    void emptyAssemblyCostsNothing() {
        Assembly a = new Assembly("Empty");
        assertEquals(0, a.getPrice().getAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, a.getWeight());
    }

    @Test
    void assemblyTotalsItsParts() {
        Assembly frontEnd = new Assembly("Front end");
        frontEnd.add(part("Fork", PartCategory.FORK, "179.99", 920));
        frontEnd.add(part("Bars", PartCategory.BARS, "89.99", 850));

        assertEquals(0, frontEnd.getPrice().getAmount().compareTo(new BigDecimal("269.98")));
        assertEquals(1770, frontEnd.getWeight());
    }

    @Test
    void nestedAssembliesTotalRecursively() {
        Assembly drivetrain = new Assembly("Drivetrain");
        drivetrain.add(part("Cranks", PartCategory.CRANKS, "329.99", 900));
        drivetrain.add(part("Sprocket", PartCategory.SPROCKET, "34.99", 110));

        Assembly bike = new Assembly("Bike");
        bike.add(part("Frame", PartCategory.FRAME, "449.99", 2150));
        bike.add(drivetrain);   // an assembly inside an assembly

        assertEquals(0, bike.getPrice().getAmount().compareTo(new BigDecimal("814.97")));
        assertEquals(3160, bike.getWeight());
        assertEquals(3, bike.getParts().size());
    }

    @Test
    void removingAPartUpdatesTheTotal() {
        Assembly a = new Assembly("Wheels");
        Part tire = part("Tire", PartCategory.TIRES, "39.99", 650);
        a.add(tire);
        a.remove(tire);

        assertTrue(a.getParts().isEmpty());
    }

    @Test
    void cannotAddDifferentCurrencies() {
        Money cad = new Money(new BigDecimal("10"), "CAD");
        Money usd = new Money(new BigDecimal("10"), "USD");

        assertThrows(IllegalArgumentException.class, () -> cad.add(usd));
    }
}