package com.salvador.bmxbuilder.recommend;

import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.RidingStyle;

/** Flatland: parts made for flatland first, and lighter helps balance tricks. */
public class FlatlandRankingStrategy implements PartRankingStrategy {

    @Override
    public RidingStyle style() {
        return RidingStyle.FLATLAND;
    }

    @Override
    public double score(Part part) {
        double fit = part.getStyleTags().contains(RidingStyle.FLATLAND) ? 50 : 0;
        return fit - part.getWeight() / 150.0;
    }
}
