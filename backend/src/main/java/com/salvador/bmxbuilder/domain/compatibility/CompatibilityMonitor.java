package com.salvador.bmxbuilder.domain.compatibility;

import com.salvador.bmxbuilder.domain.BuildChangeEvent;
import com.salvador.bmxbuilder.domain.BuildChangeListener;

/**
 * Observer pattern: a Concrete Observer. Re-checks compatibility every time the build it
 * listens to changes, and keeps the latest report.
 */
public class CompatibilityMonitor implements BuildChangeListener {

    private final CompatibilityChecker checker;
    private CompatibilityReport latest;

    public CompatibilityMonitor(CompatibilityChecker checker) {
        this.checker = checker;
    }

    @Override
    public void onBuildChanged(BuildChangeEvent event) {
        latest = checker.check(event.build());
    }

    /** The report from the last change, or null if nothing has changed yet. */
    public CompatibilityReport latestReport() {
        return latest;
    }
}
