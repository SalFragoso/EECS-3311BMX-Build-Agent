package com.salvador.bmxbuilder.service;

import com.salvador.bmxbuilder.domain.PartCategory;

import java.util.List;

/**
 * F10: two or three builds lined up slot by slot.
 *
 * @param rows one row per slot that any of the builds uses; parts holds each build's part name
 *             in the same order as builds (null where that build has nothing in the slot)
 */
public record BuildComparison(List<BuildView> builds, List<Row> rows) {

    public record Row(PartCategory slot, List<String> parts, boolean differs) {
    }
}
