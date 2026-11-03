package com.salvador.bmxbuilder.domain.compatibility;

import com.salvador.bmxbuilder.domain.PartCategory;

/** The rear hub's axle must fit the frame's rear dropouts. */
public class RearAxleRule extends MatchingSpecRule {

    public RearAxleRule() {
        super("Rear axle", PartCategory.FRAME, "rearAxle", PartCategory.REAR_HUB, "axle");
    }
}
