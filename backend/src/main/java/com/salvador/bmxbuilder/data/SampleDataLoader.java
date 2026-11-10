package com.salvador.bmxbuilder.data;

import com.salvador.bmxbuilder.domain.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(1) // seed the catalog before anything else that runs at startup (like the CLI)
public class SampleDataLoader implements CommandLineRunner {

    private final PartRepository parts;

    public SampleDataLoader(PartRepository parts) {
        this.parts = parts;
    }

    /**
     * Adds any sample part that isn't in the database yet (matched by brand + model), so new sample
     * parts show up on the next start without wiping the table, and existing ids stay the same.
     */
    @Override
    public void run(String... args) {
        Set<String> existing = parts.findAll().stream()
                .map(p -> p.getBrand() + "|" + p.getModel())
                .collect(Collectors.toSet());
        List<Part> missing = sampleParts().stream()
                .filter(p -> !existing.contains(p.getBrand() + "|" + p.getModel()))
                .toList();
        if (!missing.isEmpty()) {
            parts.saveAll(missing);
        }
    }

    // Sample data only: models and prices are illustrative, not real listings.
    static List<Part> sampleParts() {
        return List.of(
                part("Cult", "Hawk", PartCategory.FRAME, "449.99", 2150, "frame_cult_hawk.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK),
                        Map.of("topTube", "20.75", "bbType", "MID", "rearAxle", "14mm",
                                "headTube", "1-1/8", "maxTire", "2.4")),
                part("Fit", "Begin", PartCategory.FRAME, "379.99", 2300, "frame_fit_begin.png",
                        Set.of(RidingStyle.STREET),
                        Map.of("topTube", "20.5", "bbType", "MID", "rearAxle", "14mm",
                                "headTube", "1-1/8", "maxTire", "2.3")),
                part("S&M", "ATF", PartCategory.FRAME, "499.99", 2400, "frame_sm_atf.png",
                        Set.of(RidingStyle.PARK, RidingStyle.DIRT),
                        Map.of("topTube", "21", "bbType", "SPANISH", "rearAxle", "14mm",
                                "headTube", "1-1/8", "maxTire", "2.4")),
                part("Odyssey", "R32", PartCategory.FORK, "179.99", 920, "fork_odyssey_r32.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK),
                        Map.of("steerer", "1-1/8", "offset", "28mm", "axle", "10mm", "maxTire", "2.5")),
                part("Odyssey", "Vandero Pro", PartCategory.FRONT_HUB, "89.99", 300, "fhub_odyssey_vandero.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK, RidingStyle.DIRT),
                        Map.of("axle", "10mm")),
                part("Profile", "Elite Cassette", PartCategory.REAR_HUB, "249.99", 520, "rhub_profile_elite.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK, RidingStyle.DIRT),
                        Map.of("axle", "14mm", "driver", "9T")),
                part("Odyssey", "Mid BB", PartCategory.BOTTOM_BRACKET, "39.99", 120, "bb_odyssey_mid.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK, RidingStyle.DIRT),
                        Map.of("bbType", "MID", "spindle", "19mm")),
                part("Fit", "Spanish BB", PartCategory.BOTTOM_BRACKET, "44.99", 140, "bb_fit_spanish.png",
                        Set.of(RidingStyle.PARK, RidingStyle.DIRT),
                        Map.of("bbType", "SPANISH", "spindle", "19mm")),
                part("Profile", "Race Cranks", PartCategory.CRANKS, "329.99", 900, "cranks_profile_race.png",
                        Set.of(RidingStyle.RACE, RidingStyle.DIRT),
                        Map.of("spindle", "19mm")),
                part("Fit", "Key Sprocket", PartCategory.SPROCKET, "34.99", 110, "sprocket_fit_key.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK),
                        Map.of("teeth", "25", "bore", "19mm")),
                part("Cult", "Crew Bars", PartCategory.BARS, "89.99", 850, "bars_cult_crew.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK),
                        Map.of("rise", "9.25in")),
                part("Odyssey", "Path Pro", PartCategory.TIRES, "39.99", 650, "tires_odyssey_path.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK),
                        Map.of("size", "20x2.4")),
                part("Cult", "Salvation Stem", PartCategory.STEM, "79.99", 280, "stem_cult_salvation.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK),
                        Map.of("reach", "50mm", "barClamp", "22.2mm")),
                part("KMC", "Z410 Chain", PartCategory.CHAIN, "19.99", 400, "chain_kmc_z410.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK, RidingStyle.DIRT, RidingStyle.RACE),
                        Map.of("pitch", "1/2x1/8")),
                part("Odyssey", "Twisted PC Pedals", PartCategory.PEDALS, "24.99", 420, "pedals_odyssey_twisted.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK, RidingStyle.DIRT),
                        Map.of("spindle", "9/16")),
                part("Alienation", "Black Sheep Rims", PartCategory.RIMS, "119.99", 1100, "rims_alienation_blacksheep.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK),
                        Map.of("size", "20", "holes", "36")),
                part("Fit", "Barstool Seat", PartCategory.SEAT, "39.99", 350, "seat_fit_barstool.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK),
                        Map.of("type", "pivotal")),
                part("Odyssey", "Springfield Brake", PartCategory.BRAKES, "49.99", 260, "brakes_odyssey_springfield.png",
                        Set.of(RidingStyle.PARK, RidingStyle.DIRT),
                        Map.of("type", "u-brake")),
                part("Cult", "Vans Wide Tires", PartCategory.TIRES, "44.99", 760, "tires_cult_vans_wide.png",
                        Set.of(RidingStyle.STREET),
                        Map.of("size", "20x2.5")),

                // Budget and alternative parts, so the optimizer and price comparison have choices
                part("Salt", "Pro Fork", PartCategory.FORK, "99.99", 1050, "fork_salt_pro.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK, RidingStyle.DIRT),
                        Map.of("steerer", "1-1/8", "offset", "30mm", "axle", "10mm", "maxTire", "2.4")),
                part("Salt", "Classic Bars", PartCategory.BARS, "49.99", 900, "bars_salt_classic.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK, RidingStyle.DIRT),
                        Map.of("rise", "9in")),
                part("Salt", "AM Stem", PartCategory.STEM, "39.99", 320, "stem_salt_am.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK, RidingStyle.DIRT),
                        Map.of("reach", "48mm", "barClamp", "22.2mm")),
                part("Salt", "Rookie Cranks", PartCategory.CRANKS, "119.99", 1100, "cranks_salt_rookie.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK),
                        Map.of("spindle", "19mm")),
                part("Odyssey", "Thunderbolt Cranks", PartCategory.CRANKS, "279.99", 850, "cranks_odyssey_thunderbolt.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK),
                        Map.of("spindle", "22mm")),
                part("Salt", "Pro Sprocket", PartCategory.SPROCKET, "19.99", 140, "sprocket_salt_pro.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK, RidingStyle.DIRT),
                        Map.of("teeth", "25", "bore", "19mm")),
                part("Odyssey", "Grandstand Pedals", PartCategory.PEDALS, "54.99", 390, "pedals_odyssey_grandstand.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK, RidingStyle.DIRT),
                        Map.of("spindle", "9/16")),
                part("Salt", "Rookie Front Hub", PartCategory.FRONT_HUB, "39.99", 380, "fhub_salt_rookie.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK),
                        Map.of("axle", "10mm")),
                part("Salt", "Everest Cassette", PartCategory.REAR_HUB, "99.99", 600, "rhub_salt_everest.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK, RidingStyle.DIRT),
                        Map.of("axle", "14mm", "driver", "9T")),
                part("Odyssey", "Clutch Freecoaster", PartCategory.REAR_HUB, "279.99", 700, "rhub_odyssey_clutch.png",
                        Set.of(RidingStyle.STREET),
                        Map.of("axle", "14mm", "driver", "9T", "type", "freecoaster")),
                part("Salt", "Valon Rims", PartCategory.RIMS, "59.99", 1250, "rims_salt_valon.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK, RidingStyle.DIRT),
                        Map.of("size", "20", "holes", "36")),
                part("Salt", "Pivotal Seat", PartCategory.SEAT, "24.99", 380, "seat_salt_pivotal.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK, RidingStyle.DIRT),
                        Map.of("type", "pivotal")),
                part("Salt", "Tracer Tires", PartCategory.TIRES, "29.99", 700, "tires_salt_tracer.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK),
                        Map.of("size", "20x2.35")),
                part("Fit", "Race Lite Frame", PartCategory.FRAME, "399.99", 1700, "frame_fit_racelite.png",
                        Set.of(RidingStyle.RACE),
                        Map.of("topTube", "21", "bbType", "MID", "rearAxle", "14mm", "headTube", "1-1/8", "maxTire", "2.1")),
                part("Salt", "Race Tires", PartCategory.TIRES, "34.99", 520, "tires_salt_race.png",
                        Set.of(RidingStyle.RACE),
                        Map.of("size", "20x1.75"))
        );
    }

    private static Part part(String brand, String model, PartCategory category, String price,
                      double grams, String image, Set<RidingStyle> styles, Map<String, String> specs) {

        Part p = new Part(brand, model, category, new Money(new BigDecimal(price), "CAD"), grams, image);
        p.getStyleTags().addAll(styles);
        p.getSpecs().putAll(specs);
        return p;
    }
}
