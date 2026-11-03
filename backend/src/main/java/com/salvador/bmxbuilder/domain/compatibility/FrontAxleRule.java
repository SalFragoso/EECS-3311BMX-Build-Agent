package com.salvador.bmxbuilder.domain.compatibility;

import com.salvador.bmxbuilder.domain.PartCategory;

/** The front hub's axle must fit the fork's dropouts. */
public class FrontAxleRule extends MatchingSpecRule {

    public FrontAxleRule() {
        super("Front axle", PartCategory.FORK, "axle", PartCategory.FRONT_HUB, "axle");
    }
}
