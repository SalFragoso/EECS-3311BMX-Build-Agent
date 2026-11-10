package com.salvador.bmxbuilder.pricing;

import com.salvador.bmxbuilder.domain.Money;

import java.time.Instant;

/**
 * One store's offer for one part.
 *
 * @param store     the store's name
 * @param partId    which catalog part this is for
 * @param price     the store's price, in the store's currency
 * @param shipping  shipping cost, in the store's currency
 * @param totalCad  price + shipping converted to CAD, used for sorting
 * @param inStock   whether the store has it
 * @param updatedAt when this price was fetched (shown so cached prices aren't mistaken for live ones)
 */
public record PriceQuote(String store,
                         Long partId,
                         Money price,
                         Money shipping,
                         Money totalCad,
                         boolean inStock,
                         Instant updatedAt) {
}
