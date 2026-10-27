package com.salvador.bmxbuilder.domain;
import java.util.List;

public abstract class BikeComponent {

    public abstract String getName();
    public abstract Money getPrice();
    public abstract double getWeight();
    public abstract List<Part> getParts();
}
