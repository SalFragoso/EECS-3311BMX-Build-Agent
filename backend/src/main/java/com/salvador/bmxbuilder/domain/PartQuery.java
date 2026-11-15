package com.salvador.bmxbuilder.domain;

import java.math.BigDecimal;

public record PartQuery(String keyword,
                        PartCategory category,
                        BigDecimal minPrice,
                        BigDecimal maxPrice,
                        RidingStyle style) {

    public boolean matches(Part p) {
        return matchesKeyword(p)
                && (category == null || p.getCategory() == category)
                && (minPrice == null || p.getPrice().getAmount().compareTo(minPrice) >= 0)
                && (maxPrice == null || p.getPrice().getAmount().compareTo(maxPrice) <= 0)
                && (style == null || p.getStyleTags().contains(style));
    }

    /**
     * Every word of the keyword has to appear somewhere in "brand model", so "salt classic bars"
     * finds Salt + Classic Bars even though the words are split across brand and model.
     */
    private boolean matchesKeyword(Part p) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String name = (p.getBrand() + " " + p.getModel()).toLowerCase();
        for (String word : keyword.toLowerCase().trim().split("\\s+")) {
            if (!name.contains(word)) {
                return false;
            }
        }
        return true;
    }
}