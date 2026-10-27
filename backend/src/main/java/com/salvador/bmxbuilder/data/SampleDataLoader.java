package com.salvador.bmxbuilder.data;
import com.salvador.bmxbuilder.domain.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SampleDataLoader implements CommandLineRunner{

    private final PartRepository parts;

    public SampleDataLoader(PartRepository parts){
        this.parts = parts;
    }

    @Override
    public void run(String... args){
        if (parts.count() > 0) return;

        parts.saveAll(List.of(
                part("Cult", "Hawk", PartCategory.FRAME, "449.99", 2150, "frame_cult_hawk.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK),
                        Map.of("topTube", "20.75", "bbType", "MID", "rearAxle", "14mm")),
                part("Fit", "Begin", PartCategory.FRAME, "379.99", 2300, "frame_fit_begin.png",
                        Set.of(RidingStyle.STREET),
                        Map.of("topTube", "20.5", "bbType", "MID", "rearAxle", "14mm")),
                part("S&M", "ATF", PartCategory.FRAME, "499.99", 2400, "frame_sm_atf.png",
                        Set.of(RidingStyle.PARK, RidingStyle.DIRT),
                        Map.of("topTube", "21", "bbType", "SPANISH", "rearAxle", "14mm")),
                part("Odyssey", "R32", PartCategory.FORK, "179.99", 920, "fork_odyssey_r32.png",
                        Set.of(RidingStyle.STREET, RidingStyle.PARK),
                        Map.of("steerer", "1-1/8", "offset", "28mm", "axle", "10mm")),
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
                        Map.of("size", "20x2.4"))
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
