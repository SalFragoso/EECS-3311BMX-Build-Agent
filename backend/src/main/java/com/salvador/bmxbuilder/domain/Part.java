package com.salvador.bmxbuilder.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.*;

@Entity
public class Part extends BikeComponent{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String brand;
    private String model;

    @Enumerated(EnumType.STRING)
    private PartCategory category;

    @Embedded
    private Money msrp;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "part_specs", joinColumns = @JoinColumn(name = "part_id"))
    @MapKeyColumn(name = "spec_key")
    @Column(name = "spec_value")
    private Map<String, String> specs = new HashMap<>();

    private double weightGrams;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "part_style_tags", joinColumns = @JoinColumn(name = "part_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "style")
    private Set<RidingStyle> styleTags = new HashSet<>();

    private String imageLayer;

    protected Part() {
    }

    public Part(String brand, String model, PartCategory category, Money msrp, double weightGrams, String imageLayer) {
        this.brand = brand;
        this.model = model;
        this.category = category;
        this.msrp = msrp;
        this.weightGrams = weightGrams;
        this.imageLayer = imageLayer;
    }

    public Long getId() {
        return id;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public PartCategory getCategory() {
        return category;
    }

    public Map<String, String> getSpecs() {
        return specs;
    }

    public Set<RidingStyle> getStyleTags() {
        return styleTags;
    }

    public String getImageLayer() {
        return imageLayer;
    }

    @Override
    public Money getPrice() {
        return this.msrp;
    }

    @Override
    public double getWeight() {
        return this.weightGrams;
    }

    @Override
    public String getName(){
        return brand + " " + model;
    }
    @JsonIgnore //Would infinitely loop by json printing its parts forever.
    @Override
    public List<Part> getParts(){
        return List.of(this);
    }

    public String spec(String key) {
        return specs.get(key);
    }
}
