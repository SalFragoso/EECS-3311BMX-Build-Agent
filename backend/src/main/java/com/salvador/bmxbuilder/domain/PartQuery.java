package com.salvador.bmxbuilder.domain;

import java.math.BigDecimal;

public record PartQuery(String keyword,
                        PartCategory category,
                        BigDecimal minPrice,
                        BigDecimal maxPrice,
                        RidingStyle style) {

    public boolean matches(Part p) {
        return (keyword == null
                || p.getBrand().toLowerCase().contains(keyword.toLowerCase())
                || p.getModel().toLowerCase().contains(keyword.toLowerCase()))
                && (category == null || p.getCategory() == category)
                && (minPrice == null || p.getPrice().getAmount().compareTo(minPrice) >= 0)
                && (maxPrice == null || p.getPrice().getAmount().compareTo(maxPrice) <= 0)
                && (style == null || p.getStyleTags().contains(style));
    }
}