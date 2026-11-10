package com.salvador.bmxbuilder.pricing;

import com.salvador.bmxbuilder.domain.BikeBuild;
import com.salvador.bmxbuilder.domain.Money;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PricingTest {

    /** A clock the test can move forward, to check cache expiry without waiting. */
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        public ZoneId getZone() { return ZoneId.of("UTC"); }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }

    /** Counts how often the wrapped store is really asked. */
    static class CountingSource implements PriceSource {
        int calls;
        public String name() { return "Counting"; }
        public List<PriceQuote> quotes(Part part) {
            calls++;
            return List.of();
        }
    }

    private final TestClock clock = new TestClock();
    private final CurrencyConverter converter = CurrencyConverter.withSampleRates();

    private static Part part(long id, PartCategory category, String msrp) {
        Part p = new Part("Test", "Part " + id, category, new Money(new BigDecimal(msrp), "CAD"), 100, "x.png");
        try {
            Field f = Part.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(p, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return p;
    }

    private SampleStorePriceSource store(String name, String currency, String factor, String shipping) {
        return new SampleStorePriceSource(name, currency, factor, shipping, p -> true, p -> true, converter, clock);
    }

    private static void assertMoney(String expected, Money actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.getAmount()),
                "expected " + expected + " but was " + actual.getAmount());
    }

    @Test
    void usdIsConvertedToCad() {
        assertMoney("137.00", converter.convert(new Money(new BigDecimal("100"), "USD"), "CAD"));
        assertMoney("100.00", converter.convert(new Money(new BigDecimal("137"), "CAD"), "USD"));
    }

    @Test
    void unknownCurrencyIsAnError() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.convert(new Money(BigDecimal.TEN, "EUR"), "CAD"));
    }

    @Test
    void storeQuoteIncludesShippingInTheCadTotal() {
        PriceQuote q = store("Online", "CAD", "0.90", "15.00").quotes(part(1, PartCategory.FRAME, "100.00")).get(0);

        assertMoney("90.00", q.price());
        assertMoney("105.00", q.totalCad());
        assertEquals(clock.now, q.updatedAt());
    }

    @Test
    void storeThatDoesNotCarryThePartReturnsNothing() {
        SampleStorePriceSource picky = new SampleStorePriceSource("Picky", "CAD", "1", "0",
                p -> false, p -> true, converter, clock);

        assertTrue(picky.quotes(part(1, PartCategory.FRAME, "100")).isEmpty());
    }

    @Test
    void cacheAnswersRepeatLookupsUntilItExpires() {
        CountingSource store = new CountingSource();
        CachingPriceSource cached = new CachingPriceSource(store, Duration.ofMinutes(10), clock);
        Part frame = part(1, PartCategory.FRAME, "100");

        cached.quotes(frame);
        cached.quotes(frame);
        assertEquals(1, store.calls);   // second lookup came from the cache

        clock.now = clock.now.plus(Duration.ofMinutes(11));
        cached.quotes(frame);
        assertEquals(2, store.calls);   // expired, so the store was asked again
    }

    @Test
    void refreshSkipsTheCache() {
        CountingSource store = new CountingSource();
        CachingPriceSource cached = new CachingPriceSource(store, Duration.ofMinutes(10), clock);
        Part frame = part(1, PartCategory.FRAME, "100");

        cached.quotes(frame);
        cached.refresh(1L);
        cached.quotes(frame);

        assertEquals(2, store.calls);
    }

    @Test
    void quotesAreSortedCheapestFirstAndBrokenStoresAreListed() {
        PriceSource broken = new PriceSource() {
            public String name() { return "Broken Store"; }
            public List<PriceQuote> quotes(Part part) { throw new IllegalStateException("timeout"); }
        };
        PriceService service = new PriceService(List.of(
                store("Pricey", "CAD", "1.10", "0"),
                broken,
                store("Cheap", "CAD", "0.80", "5")));

        PriceComparison result = service.compare(part(1, PartCategory.FRAME, "100"), false);

        assertEquals("Cheap", result.quotes().get(0).store());
        assertEquals("Pricey", result.quotes().get(1).store());
        assertEquals(List.of("Broken Store"), result.unavailableSources());
    }

    @Test
    void outOfStockOffersGoLast() {
        SampleStorePriceSource cheapButOut = new SampleStorePriceSource("Out of stock", "CAD", "0.50", "0",
                p -> true, p -> false, converter, clock);
        PriceService service = new PriceService(List.of(cheapButOut, store("In stock", "CAD", "1.00", "0")));

        assertEquals("In stock", service.compare(part(1, PartCategory.FRAME, "100"), false).quotes().get(0).store());
    }

    @Test
    void buildTotalUsesTheCheapestInStockStore() {
        BikeBuild build = new BikeBuild("Priced");
        build.setPart(PartCategory.FRAME, part(1, PartCategory.FRAME, "400.00"));
        build.setPart(PartCategory.FORK, part(2, PartCategory.FORK, "100.00"));
        PriceService service = new PriceService(List.of(store("Online", "CAD", "0.90", "0")));

        BuildPriceSummary summary = service.cheapestBuild(build);

        assertMoney("500.00", summary.msrpTotal());
        assertMoney("450.00", summary.cheapestTotal());
        assertMoney("50.00", summary.savings());
        assertEquals(2, summary.slots().size());
    }

    @Test
    void partWithNoOffersFallsBackToMsrp() {
        BikeBuild build = new BikeBuild("Unpriced");
        build.setPart(PartCategory.FRAME, part(1, PartCategory.FRAME, "400.00"));

        BuildPriceSummary summary = new PriceService(List.of()).cheapestBuild(build);

        assertNull(summary.slots().get(0).best());
        assertMoney("400.00", summary.cheapestTotal());
    }
}
