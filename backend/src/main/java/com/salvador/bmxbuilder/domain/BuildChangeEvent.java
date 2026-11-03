package com.salvador.bmxbuilder.domain;

/**
 * What changed in a build: which slot, the part that was there before and the part there now.
 * Either part can be null (slot was empty, or the part was removed).
 */
public record BuildChangeEvent(BikeBuild build, PartCategory slot, Part oldPart, Part newPart) {
}
