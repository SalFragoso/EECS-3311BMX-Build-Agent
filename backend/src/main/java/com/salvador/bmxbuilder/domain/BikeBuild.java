package com.salvador.bmxbuilder.domain;

import jakarta.persistence.*;

import java.util.*;

/**
 * A rider's bike build: one part per slot.
 *
 * Observer pattern: this is the Subject. Every change to a slot notifies the registered
 * BuildChangeListeners.
 * Composite pattern: this is the Client. getRoot() arranges the parts into an Assembly tree,
 * and totals come from one recursive call on that tree.
 */
@Entity
public class BikeBuild {

    /** Every slot except brakes, since lots of BMX bikes run brakeless. */
    public static final Set<PartCategory> REQUIRED_SLOTS =
            Collections.unmodifiableSet(EnumSet.complementOf(EnumSet.of(PartCategory.BRAKES)));

    /** How slots are grouped into assemblies (from the F04 spec). */
    private static final Map<String, List<PartCategory>> GROUPS = new LinkedHashMap<>();

    static {
        GROUPS.put("Frame and fork", List.of(PartCategory.FRAME, PartCategory.FORK));
        GROUPS.put("Cockpit", List.of(PartCategory.BARS, PartCategory.STEM));
        GROUPS.put("Drivetrain", List.of(PartCategory.CRANKS, PartCategory.BOTTOM_BRACKET,
                PartCategory.SPROCKET, PartCategory.CHAIN, PartCategory.PEDALS));
        GROUPS.put("Wheels", List.of(PartCategory.FRONT_HUB, PartCategory.REAR_HUB,
                PartCategory.RIMS, PartCategory.TIRES));
        GROUPS.put("Contact points", List.of(PartCategory.SEAT));
        GROUPS.put("Brakes", List.of(PartCategory.BRAKES));
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    // Join table build_parts(build_id, slot, part_id). A catalog part can be in many builds.
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "build_parts",
            joinColumns = @JoinColumn(name = "build_id"),
            inverseJoinColumns = @JoinColumn(name = "part_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "slot")
    private Map<PartCategory, Part> slots = new HashMap<>();

    // Listeners only live in memory; they aren't saved to the database.
    @Transient
    private List<BuildChangeListener> listeners = new ArrayList<>();

    protected BikeBuild() {
    }

    public BikeBuild(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Puts a part in a slot (replacing whatever was there) and notifies listeners. */
    public void setPart(PartCategory slot, Part part) {
        if (part.getCategory() != slot) {
            throw new IllegalArgumentException(
                    "A " + part.getCategory() + " can't go in the " + slot + " slot");
        }
        Part old = slots.put(slot, part);
        notifyListeners(new BuildChangeEvent(this, slot, old, part));
    }

    /** Empties a slot. Nothing happens (and nobody is notified) if it was already empty. */
    public void removePart(PartCategory slot) {
        Part old = slots.remove(slot);
        if (old != null) {
            notifyListeners(new BuildChangeEvent(this, slot, old, null));
        }
    }

    public Part getPart(PartCategory slot) {
        return slots.get(slot);
    }

    public boolean hasPart(PartCategory slot) {
        return slots.containsKey(slot);
    }

    /** Read-only copy of the filled slots, in slot order. */
    public Map<PartCategory, Part> getSlots() {
        Map<PartCategory, Part> copy = new EnumMap<>(PartCategory.class);
        copy.putAll(slots);
        return Collections.unmodifiableMap(copy);
    }

    /** Builds the Composite tree: Bike -> assemblies -> parts. Empty assemblies are left out. */
    public Assembly getRoot() {
        Assembly bike = new Assembly(name);
        for (Map.Entry<String, List<PartCategory>> group : GROUPS.entrySet()) {
            Assembly assembly = new Assembly(group.getKey());
            for (PartCategory slot : group.getValue()) {
                if (slots.containsKey(slot)) {
                    assembly.add(slots.get(slot));
                }
            }
            if (!assembly.getChildren().isEmpty()) {
                bike.add(assembly);
            }
        }
        return bike;
    }

    public Money totalPrice() {
        return getRoot().getPrice();
    }

    public double totalWeight() {
        return getRoot().getWeight();
    }

    public boolean isComplete() {
        return slots.keySet().containsAll(REQUIRED_SLOTS);
    }

    public List<PartCategory> missingSlots() {
        List<PartCategory> missing = new ArrayList<>();
        for (PartCategory slot : REQUIRED_SLOTS) {
            if (!slots.containsKey(slot)) {
                missing.add(slot);
            }
        }
        return missing;
    }

    /**
     * A throwaway copy of this build with one extra part, used to ask "would this part fit?"
     * without changing the real build. The copy has no id and no listeners.
     */
    public BikeBuild withPart(Part candidate) {
        BikeBuild copy = new BikeBuild(name);
        copy.slots.putAll(slots);
        copy.slots.put(candidate.getCategory(), candidate);
        return copy;
    }

    public void addListener(BuildChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(BuildChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(BuildChangeEvent event) {
        for (BuildChangeListener listener : List.copyOf(listeners)) {
            listener.onBuildChanged(event);
        }
    }
}
