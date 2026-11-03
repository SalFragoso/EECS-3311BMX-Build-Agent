package com.salvador.bmxbuilder.domain.compatibility;

/** Result of one rule. NOT_CHECKED means the parts that rule needs aren't picked yet. */
public enum Status {
    PASS, WARNING, FAIL, NOT_CHECKED
}
