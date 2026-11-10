package com.salvador.bmxbuilder.pricing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * The stores the app compares. Each one is wrapped in a CachingPriceSource (Decorator), so adding
 * caching didn't require touching any store class. These are sample stores built on the local
 * dataset; a real store API would be one more PriceSource here.
 */
@Configuration
public class PricingConfig {

    static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final CurrencyConverter converter = CurrencyConverter.withSampleRates();
    private final Clock clock = Clock.systemUTC();

    @Bean
    public PriceSource sampleLocalShop() {
        // local shop: list price, free pickup, a few parts out of stock
        return cached(new SampleStorePriceSource("Sample Local Shop (Toronto)", "CAD", "1.00", "0.00",
                part -> true, part -> part.getId() % 5 != 0, converter, clock));
    }

    @Bean
    public PriceSource sampleOnlineStore() {
        // online: cheaper, flat shipping, everything in stock
        return cached(new SampleStorePriceSource("Sample Online Store (CA)", "CAD", "0.92", "14.99",
                part -> true, part -> true, converter, clock));
    }

    @Bean
    public PriceSource sampleUsStore() {
        // US store: priced in USD, higher shipping, only carries some parts
        return cached(new SampleStorePriceSource("Sample US Store", "USD", "0.85", "24.99",
                part -> part.getId() % 3 != 0, part -> true, converter, clock));
    }

    private PriceSource cached(PriceSource source) {
        return new CachingPriceSource(source, CACHE_TTL, clock);
    }
}
