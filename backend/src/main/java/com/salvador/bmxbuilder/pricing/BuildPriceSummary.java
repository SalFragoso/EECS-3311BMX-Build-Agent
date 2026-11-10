package com.salvador.bmxbuilder.pricing;

import com.salvador.bmxbuilder.domain.Money;
import com.salvador.bmxbuilder.domain.PartCategory;

import java.util.List;

/**
 * The cheapest way to buy a whole build: the best in-stock offer for each slot.
 *
 * @param msrpTotal     the build total at catalog MSRP
 * @param cheapestTotal the total buying each part from its cheapest in-stock store
 * @param savings       msrpTotal - cheapestTotal
 */
public record BuildPriceSummary(Long buildId,
                                List<SlotPrice> slots,
                                Money msrpTotal,
                                Money cheapestTotal,
                                Money savings) {

    /** best is null when no store has the part in stock; the MSRP is used for the total instead. */
    public record SlotPrice(PartCategory slot, Long partId, String partName, Money msrp, PriceQuote best) {
    }
}
