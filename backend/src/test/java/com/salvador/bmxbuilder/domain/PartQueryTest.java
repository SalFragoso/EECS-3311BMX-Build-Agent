package com.salvador.bmxbuilder.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PartQueryTest {

    // Helper: a street frame at a given price
    private Part frame(String price) {
        Part p = new Part("Cult", "Hawk", PartCategory.FRAME,
                new Money(new BigDecimal(price), "CAD"), 2150, "frame.png");
        p.getStyleTags().add(RidingStyle.STREET);
        return p;
    }

    @Test
    void keywordMatchesBrandIgnoringCase() {
        PartQuery q = new PartQuery("cult", null, null, null, null);
        assertTrue(q.matches(frame("449.99")));
    }

    @Test
    void emptyQueryMatchesEverything() {
        PartQuery q = new PartQuery(null, null, null, null, null);
        assertTrue(q.matches(frame("449.99")));
    }

    @Test
    void wrongCategoryDoesNotMatch() {
        PartQuery q = new PartQuery(null, PartCategory.FORK, null, null, null);
        assertFalse(q.matches(frame("449.99")));
    }

    @Test
    void partAboveMaxPriceDoesNotMatch() {
        PartQuery q = new PartQuery(null, null, null, new BigDecimal("400"), null);
        assertFalse(q.matches(frame("449.99")));
    }

    @Test
    void priceEqualToMaxStillMatches() {
        PartQuery q = new PartQuery(null, null, null, new BigDecimal("449.99"), null);
        assertTrue(q.matches(frame("449.99")));
    }

    @Test
    void styleNotInTagsDoesNotMatch() {
        PartQuery q = new PartQuery(null, null, null, null, RidingStyle.RACE);
        assertFalse(q.matches(frame("449.99")));
    }
}