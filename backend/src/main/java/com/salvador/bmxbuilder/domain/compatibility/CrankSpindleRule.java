package com.salvador.bmxbuilder.domain.compatibility;

import com.salvador.bmxbuilder.domain.PartCategory;

/** The cranks' spindle must fit the bottom bracket's bearings. */
public class CrankSpindleRule extends MatchingSpecRule {

    public CrankSpindleRule() {
        super("Crank spindle", PartCategory.BOTTOM_BRACKET, "spindle", PartCategory.CRANKS, "spindle");
    }
}
