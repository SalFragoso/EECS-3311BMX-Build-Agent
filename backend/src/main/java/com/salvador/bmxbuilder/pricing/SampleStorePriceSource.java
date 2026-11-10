package com.salvador.bmxbuilder.pricing;

import com.salvador.bmxbuilder.domain.Money;
import com.salvador.bmxbuilder.domain.Part;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.function.Predicate;

/**
 * A local sample price dataset standing in for a real store (Stage 1: "a local price dataset where
 * no store API exists"). Prices are derived from each part's MSRP with a store-specific markup, so
 * the data is consistent and repeatable. The store names say "Sample" so nobody mistakes them for real shops.
 */
public class SampleStorePriceSource implements PriceSource {

    private final String name;
    private final String currency;
    private final BigDecimal priceFactor;     // store price = MSRP (in CAD) x factor, in the store's currency
    private final BigDecimal shipping;        // flat shipping in the store's currency
    private final Predicate<Part> carries;    // which parts this store sells
    private final Predicate<Part> inStock;
    private final CurrencyConverter converter;
    private final Clock clock;

    public SampleStorePriceSource(String name, String currency, String priceFactor, String shipping,
                                  Predicate<Part> carries, Predicate<Part> inStock,
                                  CurrencyConverter converter, Clock clock) {
        this.name = name;
        this.currency = currency;
        this.priceFactor = new BigDecimal(priceFactor);
        this.shipping = new BigDecimal(shipping);
        this.carries = carries;
        this.inStock = inStock;
        this.converter = converter;
        this.clock = clock;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<PriceQuote> quotes(Part part) {
        if (!carries.test(part)) {
            return List.of();
        }
        BigDecimal msrpInStoreCurrency = converter.convert(part.getPrice(), currency).getAmount();
        BigDecimal price = msrpInStoreCurrency.multiply(priceFactor).setScale(2, RoundingMode.HALF_UP);
        Money total = converter.convert(new Money(price.add(shipping), currency), "CAD");
        return List.of(new PriceQuote(name, part.getId(),
                new Money(price, currency),
                new Money(shipping.setScale(2, RoundingMode.HALF_UP), currency),
                total,
                inStock.test(part),
                clock.instant()));
    }
}
