package com.salvador.bmxbuilder.service;

import com.salvador.bmxbuilder.data.BuildRepository;
import com.salvador.bmxbuilder.data.PartRepository;
import com.salvador.bmxbuilder.domain.BikeBuild;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import com.salvador.bmxbuilder.domain.PartQuery;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityChecker;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityMonitor;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityReport;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Facade pattern: the single entry point for the REST controllers and the CLI.
 * They never touch repositories or domain logic directly.
 */
@Service
public class BuildService {

    private final PartRepository parts;
    private final BuildRepository builds;
    private final CompatibilityChecker checker;

    public BuildService(PartRepository parts, BuildRepository builds, CompatibilityChecker checker) {
        this.parts = parts;
        this.builds = builds;
        this.checker = checker;
    }

    // ---------- F03: catalog search ----------

    public List<Part> searchParts(PartQuery query) {
        if (query.minPrice() != null && query.maxPrice() != null
                && query.minPrice().compareTo(query.maxPrice()) > 0) {
            throw new IllegalArgumentException("Min price must be below max price.");
        }
        return parts.search(query);
    }

    public Part getPart(Long partId) {
        return parts.findById(partId)
                .orElseThrow(() -> new NotFoundException("Part " + partId + " not found"));
    }

    // ---------- F04: build configurator ----------

    public BuildView createBuild(String name) {
        String cleanName = (name == null || name.isBlank()) ? "Untitled build" : name.trim();
        BikeBuild saved = builds.save(new BikeBuild(cleanName));
        return BuildView.of(saved, checker.check(saved));
    }

    public BuildView viewBuild(Long buildId) {
        BikeBuild build = loadBuild(buildId);
        return BuildView.of(build, checker.check(build));
    }

    public List<BuildView> listBuilds() {
        return builds.findAll().stream()
                .map(b -> BuildView.of(b, checker.check(b)))
                .toList();
    }

    /**
     * Puts a catalog part into a slot. A part that doesn't fit is still allowed (F04 says flag it,
     * don't block it), and the returned view carries the compatibility report.
     */
    public BuildView selectPart(Long buildId, PartCategory slot, Long partId) {
        BikeBuild build = loadBuild(buildId);
        Part part = getPart(partId);

        // Observer pattern in action: the monitor re-checks compatibility when the build changes
        CompatibilityMonitor monitor = new CompatibilityMonitor(checker);
        build.addListener(monitor);
        build.setPart(slot, part);

        BikeBuild saved = builds.save(build);
        return BuildView.of(saved, reportFrom(monitor, saved));
    }

    public BuildView removePart(Long buildId, PartCategory slot) {
        BikeBuild build = loadBuild(buildId);

        CompatibilityMonitor monitor = new CompatibilityMonitor(checker);
        build.addListener(monitor);
        build.removePart(slot);

        BikeBuild saved = builds.save(build);
        return BuildView.of(saved, reportFrom(monitor, saved));
    }

    // ---------- F05: compatibility ----------

    public CompatibilityReport checkCompatibility(Long buildId) {
        return checker.check(loadBuild(buildId));
    }

    /** Catalog parts for a slot that would fit the build as it is now. */
    public List<Part> compatibleParts(Long buildId, PartCategory slot) {
        BikeBuild build = loadBuild(buildId);
        return parts.findByCategory(slot).stream()
                .filter(p -> checker.isCompatible(build, p))
                .toList();
    }

    // ---------- helpers ----------

    private BikeBuild loadBuild(Long buildId) {
        return builds.findById(buildId)
                .orElseThrow(() -> new NotFoundException("Build " + buildId + " not found"));
    }

    /** If nothing changed (e.g. removing an empty slot), the monitor never ran, so check directly. */
    private CompatibilityReport reportFrom(CompatibilityMonitor monitor, BikeBuild build) {
        CompatibilityReport report = monitor.latestReport();
        return (report != null) ? report : checker.check(build);
    }
}
