package com.salvador.bmxbuilder.pricing;

import com.salvador.bmxbuilder.domain.Part;

import java.util.List;

/**
 * Somewhere that sells parts. Each store (an API, a scraped feed, a local dataset) adapts its own
 * data to this interface, so PriceService treats them all the same way.
 * Decorator pattern: this is the Component; CachingPriceSource wraps any source with a cache.
 */
public interface PriceSource {

    String name();

    /** This store's offers for the part (empty if it doesn't carry it). May throw if the store is down. */
    List<PriceQuote> quotes(Part part);

    /** Drop any cached prices for the part so the next call fetches fresh ones. No-op if not cached. */
    default void refresh(Long partId) {
    }
}
