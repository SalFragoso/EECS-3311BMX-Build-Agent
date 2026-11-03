package com.salvador.bmxbuilder.domain.compatibility;

import com.salvador.bmxbuilder.domain.PartCategory;

/** The bottom bracket must fit the frame's shell (Mid, Spanish, American). */
public class BottomBracketRule extends MatchingSpecRule {

    public BottomBracketRule() {
        super("Bottom bracket shell", PartCategory.FRAME, "bbType", PartCategory.BOTTOM_BRACKET, "bbType");
    }
}
