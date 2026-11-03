package com.salvador.bmxbuilder.domain.compatibility;

import com.salvador.bmxbuilder.domain.PartCategory;

/** The sprocket's bore must fit the crank spindle. */
public class SprocketBoreRule extends MatchingSpecRule {

    public SprocketBoreRule() {
        super("Sprocket bore", PartCategory.CRANKS, "spindle", PartCategory.SPROCKET, "bore");
    }
}
