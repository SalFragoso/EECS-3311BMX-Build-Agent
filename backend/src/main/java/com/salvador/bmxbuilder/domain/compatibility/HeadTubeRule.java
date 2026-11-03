package com.salvador.bmxbuilder.domain.compatibility;

import com.salvador.bmxbuilder.domain.PartCategory;

/** The fork's steerer must fit the frame's head tube. */
public class HeadTubeRule extends MatchingSpecRule {

    public HeadTubeRule() {
        super("Head tube", PartCategory.FRAME, "headTube", PartCategory.FORK, "steerer");
    }
}
