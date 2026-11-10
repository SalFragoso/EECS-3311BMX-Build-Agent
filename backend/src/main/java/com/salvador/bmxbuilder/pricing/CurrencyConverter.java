package com.salvador.bmxbuilder.pricing;

import com.salvador.bmxbuilder.domain.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Converts between CAD and other currencies using fixed rates (how many CAD one unit is worth).
 * Fixed sample rates keep results repeatable; a live FX API could replace this later.
 */
public class CurrencyConverter {

    private final Map<String, BigDecimal> cadPerUnit;

    public CurrencyConverter(Map<String, BigDecimal> cadPerUnit) {
        this.cadPerUnit = Map.copyOf(cadPerUnit);
    }

    /** CAD plus a sample USD rate. */
    public static CurrencyConverter withSampleRates() {
        return new CurrencyConverter(Map.of("CAD", BigDecimal.ONE, "USD", new BigDecimal("1.37")));
    }

    public Money convert(Money money, String toCurrency) {
        if (money.getCurrency().equals(toCurrency)) {
            return money;
        }
        BigDecimal inCad = money.getAmount().multiply(rate(money.getCurrency()));
        BigDecimal converted = inCad.divide(rate(toCurrency), 2, RoundingMode.HALF_UP);
        return new Money(converted, toCurrency);
    }

    private BigDecimal rate(String currency) {
        BigDecimal rate = cadPerUnit.get(currency);
        if (rate == null) {
            throw new IllegalArgumentException("No exchange rate for " + currency);
        }
        return rate;
    }
}
