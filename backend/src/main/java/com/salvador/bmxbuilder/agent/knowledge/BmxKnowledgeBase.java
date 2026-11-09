package com.salvador.bmxbuilder.agent.knowledge;

import java.util.List;

/**
 * The built-in articles the Q&A assistant can cite. General BMX background written for this
 * project; figures are typical ranges, not manufacturer specs.
 */
final class BmxKnowledgeBase {

    private BmxKnowledgeBase() {
    }

    static List<KnowledgeDoc> articles() {
        return List.of(
                new KnowledgeDoc("kb-bottom-brackets", "Bottom bracket types: Mid, Spanish and American",
                        "The bottom bracket (BB) is the bearing set in the frame's BB shell that the crank spindle "
                                + "turns in, and it has to match the shell. Mid BBs are the most common on modern "
                                + "frames: sealed bearings press into a mid-size shell. Spanish BBs press bearings "
                                + "into a slightly smaller shell and are found on some frames from specific brands. "
                                + "American BBs use a large shell with cups, mostly on older or entry-level bikes. "
                                + "The BB's bearing size must also match the crank spindle, usually 19mm or 22mm on "
                                + "three-piece cranks.",
                        List.of("bb", "bottom bracket", "mid", "spanish", "american", "shell", "spindle", "crank")),

                new KnowledgeDoc("kb-freecoaster-cassette", "Freecoaster vs cassette rear hubs",
                        "A cassette hub engages as soon as you pedal forward and lets you coast, but the cranks turn "
                                + "if you roll backwards. A freecoaster lets the bike roll backwards (fakie) without "
                                + "the cranks spinning, which suits street riders who do lots of fakies and "
                                + "rolling tricks. Freecoasters have a little 'slack' before they engage and are "
                                + "usually heavier and more expensive. Park, dirt and race riders mostly use cassettes.",
                        List.of("freecoaster", "cassette", "hub", "rear", "fakie", "slack", "driver")),

                new KnowledgeDoc("kb-gearing", "BMX gearing and sprocket sizes",
                        "Gearing is the sprocket's tooth count divided by the rear hub driver's. A common modern "
                                + "freestyle setup is a 25 tooth sprocket with a 9 tooth driver (about 2.78), which is "
                                + "easy to spin up for street and park. A bigger ratio gives more top speed for dirt "
                                + "or race but is harder to start. The sprocket's bore has to fit the crank spindle.",
                        List.of("gearing", "gear", "ratio", "sprocket", "teeth", "driver", "25", "9t")),

                new KnowledgeDoc("kb-top-tube", "Choosing a frame top tube length",
                        "Top tube (TT) length is the main frame sizing number. As a rough guide, riders under about "
                                + "5'6\" often ride around 20\", riders between about 5'6\" and 6' often ride 20.5\" "
                                + "to 21\", and taller riders 21\" or longer. Shorter feels quicker for spins and "
                                + "technical street riding; longer is more stable at speed, in bowls and on dirt.",
                        List.of("top tube", "tt", "frame", "size", "sizing", "height", "length", "tall")),

                new KnowledgeDoc("kb-axles", "Hub axles and dropouts",
                        "Most modern freestyle frames take a 14mm rear axle, and most forks take a 10mm front axle. "
                                + "Some older or budget bikes use 3/8\" axles. The hub's axle has to match the "
                                + "dropouts, so a 14mm rear hub needs a frame with 14mm dropouts, and a 10mm front "
                                + "hub needs a fork with 10mm dropouts.",
                        List.of("axle", "dropout", "hub", "14mm", "10mm", "front", "rear")),

                new KnowledgeDoc("kb-brakes", "Brakes or brakeless",
                        "Many street and park riders ride brakeless, which removes cables and makes bar spins "
                                + "easier. Others keep a rear U-brake for control, especially in parks with fast "
                                + "transitions. To run a brake, the frame needs brake mounts. A detangler (gyro) lets "
                                + "the bars spin all the way around while keeping the brake cable connected.",
                        List.of("brake", "brakeless", "u-brake", "gyro", "detangler", "mount", "barspin")),

                new KnowledgeDoc("kb-tires", "Tire width and clearance",
                        "Street and park riders often run wide tires, around 2.3\" to 2.5\", for grip and cushioning. "
                                + "Race riders use narrower, lighter tires. Before fitting wider tires, check the "
                                + "frame's and fork's maximum tire clearance, since a tire that's too wide can rub. "
                                + "Higher pressure rolls faster; lower pressure is softer on landings.",
                        List.of("tire", "tyre", "width", "wide", "clearance", "pressure", "2.4", "2.5")),

                new KnowledgeDoc("kb-headset-steerer", "Forks, steerers and headsets",
                        "Modern BMX forks almost all use a 1-1/8\" steerer tube with an integrated headset, where the "
                                + "bearings sit directly in the frame's head tube. The fork steerer must match the "
                                + "head tube. Fork offset changes handling: less offset feels twitchier and is "
                                + "popular for technical street and nose manuals; more offset feels more stable.",
                        List.of("fork", "steerer", "headset", "head tube", "offset", "integrated")),

                new KnowledgeDoc("kb-riding-styles", "BMX riding styles",
                        "Street riding uses rails, ledges and stairs, so riders want strong, often heavier parts. "
                                + "Park is ridden in skateparks on ramps and bowls, with an all-round setup. Dirt (trails) "
                                + "is jumping built dirt lines, favouring stability. Flatland is tricks on flat ground "
                                + "with special short frames. Race is BMX racing on a track, where light parts and "
                                + "brakes are standard.",
                        List.of("street", "park", "dirt", "trails", "flatland", "race", "style")),

                new KnowledgeDoc("kb-chain", "Chains and chain tension",
                        "BMX bikes use a 1/2\" x 1/8\" single-speed chain. A half-link chain lets you shorten it one "
                                + "link at a time, so you can set the wheel position more precisely. The chain should "
                                + "be tight enough not to fall off but not so tight that it binds; adjust tension by "
                                + "moving the rear wheel in the dropouts.",
                        List.of("chain", "half-link", "tension", "link"))
        );
    }
}
