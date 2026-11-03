package com.salvador.bmxbuilder.domain;

/**
 * Observer pattern: the Observer role. Anything that wants to react when a build changes
 * (compatibility checking, preview rendering) implements this and registers with BikeBuild.
 */
public interface BuildChangeListener {
    void onBuildChanged(BuildChangeEvent event);
}
