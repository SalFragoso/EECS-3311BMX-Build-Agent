package com.salvador.bmxbuilder.domain.compatibility;

import com.salvador.bmxbuilder.domain.Part;

import java.util.List;

/** What one rule found: its name, status, a human-readable reason and the parts involved. */
public record RuleResult(String rule, Status status, String message, List<Part> parts) {

    public static RuleResult notChecked(String rule) {
        return new RuleResult(rule, Status.NOT_CHECKED, "Not checked yet: parts missing", List.of());
    }
}
