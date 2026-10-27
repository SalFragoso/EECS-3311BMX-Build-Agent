package com.salvador.bmxbuilder.domain;
import java.math.BigDecimal;

public record PartQuery(String keyword,
        PartCategory category,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        RidingStyle style) {
}
