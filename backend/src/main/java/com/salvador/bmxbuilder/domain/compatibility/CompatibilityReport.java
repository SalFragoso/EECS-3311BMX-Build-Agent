package com.salvador.bmxbuilder.domain.compatibility;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Every rule's result for one build, plus an overall status. */
public record CompatibilityReport(List<RuleResult> results) {

    /**
     * FAIL if any rule failed, otherwise WARNING if any warned, otherwise PASS.
     * If no rule could run yet (empty build), NOT_CHECKED.
     */
    @JsonProperty("overall")
    public Status overall() {
        boolean anyChecked = false;
        boolean anyWarning = false;
        for (RuleResult r : results) {
            if (r.status() == Status.FAIL) {
                return Status.FAIL;
            }
            if (r.status() == Status.WARNING) {
                anyWarning = true;
            }
            if (r.status() != Status.NOT_CHECKED) {
                anyChecked = true;
            }
        }
        if (!anyChecked) {
            return Status.NOT_CHECKED;
        }
        return anyWarning ? Status.WARNING : Status.PASS;
    }

    /** Only the results that need attention (fails and warnings). */
    @JsonProperty("issues")
    public List<RuleResult> issues() {
        return results.stream()
                .filter(r -> r.status() == Status.FAIL || r.status() == Status.WARNING)
                .toList();
    }
}
