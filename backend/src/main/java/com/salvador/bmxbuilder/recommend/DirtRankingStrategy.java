package com.salvador.bmxbuilder.recommend;

import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.RidingStyle;

/** Dirt: parts made for dirt first, with a slight preference for lighter parts on long jump lines. */
public class DirtRankingStrategy implements PartRankingStrategy {

    @Override
    public RidingStyle style() {
        return RidingStyle.DIRT;
    }

    @Override
    public double score(Part part) {
        double fit = part.getStyleTags().contains(RidingStyle.DIRT) ? 50 : 0;
        return fit - part.getWeight() / 300.0;
    }
}
