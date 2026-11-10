package com.salvador.bmxbuilder.pricing;

import com.salvador.bmxbuilder.domain.Money;

import java.util.List;

/**
 * Every store's offer for one part, cheapest total first.
 *
 * @param quotes              offers sorted by total cost in CAD (in-stock offers first)
 * @param unavailableSources  stores that failed to answer
 * @param estimate            the catalog MSRP, shown as an estimate when no store has a price
 */
public record PriceComparison(Long partId,
                              String partName,
                              List<PriceQuote> quotes,
                              List<String> unavailableSources,
                              Money estimate) {

    public boolean hasQuotes() {
        return !quotes.isEmpty();
    }
}
