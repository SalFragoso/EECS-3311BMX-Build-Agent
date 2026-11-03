package com.salvador.bmxbuilder.service;

/** Thrown when a build or part id doesn't exist. The web layer turns it into a 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
