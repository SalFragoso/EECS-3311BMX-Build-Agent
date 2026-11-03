package com.salvador.bmxbuilder.service;

import com.salvador.bmxbuilder.domain.BikeBuild;
import com.salvador.bmxbuilder.domain.Money;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityReport;

import java.util.List;
import java.util.Map;

/**
 * Everything the GUI or CLI needs to show a build in one object:
 * its parts, totals, what's missing and the compatibility report.
 * The entity itself is never sent out, so the API shape doesn't depend on the database.
 */
public record BuildView(Long id,
                        String name,
                        Map<PartCategory, Part> parts,
                        Money totalPrice,
                        double totalWeightGrams,
                        boolean complete,
                        List<PartCategory> missingSlots,
                        CompatibilityReport compatibility) {

    public static BuildView of(BikeBuild build, CompatibilityReport report) {
        return new BuildView(
                build.getId(),
                build.getName(),
                build.getSlots(),
                build.totalPrice(),
                build.totalWeight(),
                build.isComplete(),
                build.missingSlots(),
                report);
    }
}
