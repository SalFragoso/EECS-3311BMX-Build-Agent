package com.salvador.bmxbuilder.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Assembly extends BikeComponent {

    private final String name;
    private final List<BikeComponent> children = new ArrayList<>();

    public Assembly(String name) {
        this.name = name;
    }

    public void add(BikeComponent component) {
        children.add(component);
    }

    public void remove(BikeComponent component) {
        children.remove(component);
    }

    public List<BikeComponent> getChildren() {
        return List.copyOf(children);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Money getPrice() {
        Money total = new Money(BigDecimal.ZERO, "CAD");
        for (BikeComponent c : children) {
            total = total.add(c.getPrice());
        }
        return total;
    }

    @Override
    public double getWeight() {
        double total = 0;
        for (BikeComponent c : children) {
            total += c.getWeight();
        }
        return total;
    }

    @Override
    public List<Part> getParts() {
        List<Part> all = new ArrayList<>();
        for (BikeComponent c : children) {
            all.addAll(c.getParts());
        }
        return all;
    }
}