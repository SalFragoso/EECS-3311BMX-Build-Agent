package com.salvador.bmxbuilder.pricing;

import com.salvador.bmxbuilder.domain.Part;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decorator pattern: the Concrete Decorator. Wraps any PriceSource and remembers its answers for a
 * while, so repeated lookups (the same part on several pages, or a whole build) don't hit the
 * store every time. To callers it's just another PriceSource; the wrapped source doesn't change.
 */
public class CachingPriceSource implements PriceSource {

    private record Entry(List<PriceQuote> quotes, Instant storedAt) {
    }

    private final PriceSource inner;
    private final Duration ttl;
    private final Clock clock;
    private final Map<Long, Entry> cache = new HashMap<>();

    public CachingPriceSource(PriceSource inner, Duration ttl, Clock clock) {
        this.inner = inner;
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public String name() {
        return inner.name();
    }

    @Override
    public synchronized List<PriceQuote> quotes(Part part) {
        Entry entry = cache.get(part.getId());
        Instant now = clock.instant();
        if (entry != null && entry.storedAt().plus(ttl).isAfter(now)) {
            return entry.quotes();   // still fresh: the quotes keep their original updatedAt time
        }
        List<PriceQuote> fresh = inner.quotes(part);
        cache.put(part.getId(), new Entry(List.copyOf(fresh), now));
        return fresh;
    }

    @Override
    public synchronized void refresh(Long partId) {
        cache.remove(partId);
        inner.refresh(partId);
    }
}
