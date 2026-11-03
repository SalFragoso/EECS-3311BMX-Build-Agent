package com.salvador.bmxbuilder.data;

import com.salvador.bmxbuilder.domain.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Order(1) // seed the catalog before anything else that runs at startup (like the CLI)
public class SampleDataLoader implements CommandLineRunner {

    private final PartRepository parts;

    public SampleDataLoader(PartRepository parts) {
        this.parts = parts;
    }

    @Override
    public void run(String... args) {
        if (parts.count() > 0) return;

        // Sample data only: models and prices are illustrative, not real listings.
        parts.saveAll(List.of(
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
                        Map.of("size", "20x2.5"))
        ));
    }

    private Part part(String brand, String model, PartCategory category, String price,
                      double grams, String image, Set<RidingStyle> styles, Map<String, String> specs) {

        Part p = new Part(brand, model, category, new Money(new BigDecimal(price), "CAD"), grams, image);
        p.getStyleTags().addAll(styles);
        p.getSpecs().putAll(specs);
        return p;
    }
}
