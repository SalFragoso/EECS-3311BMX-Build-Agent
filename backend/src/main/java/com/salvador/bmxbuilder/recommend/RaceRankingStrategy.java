package com.salvador.bmxbuilder.recommend;

import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.RidingStyle;

/** Race: parts made for racing first, and lighter is clearly better. */
public class RaceRankingStrategy implements PartRankingStrategy {

    @Override
    public RidingStyle style() {
        return RidingStyle.RACE;
    }

    @Override
    public double score(Part part) {
        double fit = part.getStyleTags().contains(RidingStyle.RACE) ? 50 : 0;
        return fit - part.getWeight() / 50.0;
    }
}
