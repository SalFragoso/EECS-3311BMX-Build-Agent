package com.salvador.bmxbuilder.domain.compatibility;

import com.salvador.bmxbuilder.domain.BikeBuild;

/** One compatibility check, e.g. "the bottom bracket fits the frame's shell". */
public interface CompatibilityRule {

    String name();

    /** True when the build has the parts this rule needs. */
    boolean appliesTo(BikeBuild build);

    /** Only called when appliesTo() is true. */
    RuleResult evaluate(BikeBuild build);
}
