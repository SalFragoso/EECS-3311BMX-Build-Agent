package com.salvador.bmxbuilder.recommend;

import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.RidingStyle;

/** Street: parts made for street first, and heavier usually means tougher, so weight counts slightly in its favour. */
public class StreetRankingStrategy implements PartRankingStrategy {

    @Override
    public RidingStyle style() {
        return RidingStyle.STREET;
    }

    @Override
    public double score(Part part) {
        double fit = part.getStyleTags().contains(RidingStyle.STREET) ? 50 : 0;
        return fit + part.getWeight() / 100.0;
    }
}
