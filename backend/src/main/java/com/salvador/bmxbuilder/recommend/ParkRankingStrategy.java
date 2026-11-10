package com.salvador.bmxbuilder.recommend;

import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.RidingStyle;

/** Park: parts made for park first, then a small bonus for lighter parts (easier to throw around on ramps). */
public class ParkRankingStrategy implements PartRankingStrategy {

    @Override
    public RidingStyle style() {
        return RidingStyle.PARK;
    }

    @Override
    public double score(Part part) {
        double fit = part.getStyleTags().contains(RidingStyle.PARK) ? 50 : 0;
        return fit - part.getWeight() / 400.0;
    }
}
