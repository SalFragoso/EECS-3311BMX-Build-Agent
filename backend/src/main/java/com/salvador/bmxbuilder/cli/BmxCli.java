package com.salvador.bmxbuilder.cli;

import com.salvador.bmxbuilder.domain.Money;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import com.salvador.bmxbuilder.domain.PartQuery;
import com.salvador.bmxbuilder.domain.RidingStyle;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityReport;
import com.salvador.bmxbuilder.domain.compatibility.RuleResult;
import com.salvador.bmxbuilder.domain.compatibility.Status;
import com.salvador.bmxbuilder.pricing.BuildPriceSummary;
import com.salvador.bmxbuilder.pricing.PriceComparison;
import com.salvador.bmxbuilder.pricing.PriceQuote;
import com.salvador.bmxbuilder.recommend.BudgetOptimizer;
import com.salvador.bmxbuilder.recommend.RiderProfile;
import com.salvador.bmxbuilder.recommend.StyleRecommendation;
import com.salvador.bmxbuilder.recommend.UpgradeAdvisor;
import com.salvador.bmxbuilder.service.AgentAnswer;
import com.salvador.bmxbuilder.service.BuildComparison;
import com.salvador.bmxbuilder.service.OptimizationResult;
import com.salvador.bmxbuilder.service.BuildService;
import com.salvador.bmxbuilder.service.BuildView;
import com.salvador.bmxbuilder.service.NotFoundException;
import com.salvador.bmxbuilder.service.PlanResult;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Command-line interface. Only active with the "cli" profile, so the normal web app is unaffected.
 * Like the REST controllers, it only talks to BuildService (the Facade), so the CLI and GUI
 * always behave the same.
 */
@Component
@Profile("cli")
@Order(2) // after SampleDataLoader
public class BmxCli implements CommandLineRunner {

    private final BuildService service;
    private Long currentBuildId;   // the build that add/remove/show/check work on
    private RidingStyle lastStyle;  // from 'recommend', used by 'optimize'

    public BmxCli(BuildService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        repl(new Scanner(System.in), System.out);
    }

    /** The read-eval-print loop. Takes its input and output as parameters so it can be tested. */
    void repl(Scanner in, PrintStream out) {
        out.println("BMX Build Agent CLI. Type 'help' for commands, 'quit' to exit.");
        while (true) {
            out.print("bmx> ");
            if (!in.hasNextLine()) {
                return;
            }
            String line = in.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] words = line.split("\\s+");
            String cmd = words[0].toLowerCase();
            String[] args = Arrays.copyOfRange(words, 1, words.length);

            if (cmd.equals("quit") || cmd.equals("exit")) {
                out.println("Bye!");
                return;
            }
            try {
                dispatch(cmd, args, out);
            } catch (IllegalArgumentException | NotFoundException e) {
                out.println("Error: " + e.getMessage());
            }
        }
    }

    private void dispatch(String cmd, String[] args, PrintStream out) {
        switch (cmd) {
            case "help" -> printHelp(out);
            case "search" -> printParts(service.searchParts(parseQuery(args)), out);
            case "builds" -> service.listBuilds().forEach(b ->
                    out.printf("  [%d] %s  %s  %s%n", b.id(), b.name(), money(b.totalPrice()),
                            b.complete() ? "complete" : b.missingSlots().size() + " slots missing"));
            case "new" -> {
                BuildView view = service.createBuild(String.join(" ", args));
                currentBuildId = view.id();
                out.println("Created build " + view.id() + " (" + view.name() + "). It's now selected.");
            }
            case "use" -> {
                currentBuildId = parseId(arg(args, 0, "use <buildId>"));
                printBuild(service.viewBuild(currentBuildId), out);
            }
            case "show" -> printBuild(service.viewBuild(requireBuild()), out);
            case "add" -> {
                PartCategory slot = parseSlot(arg(args, 0, "add <SLOT> <partId>"));
                Long partId = parseId(arg(args, 1, "add <SLOT> <partId>"));
                printBuild(service.selectPart(requireBuild(), slot, partId), out);
            }
            case "remove" -> {
                PartCategory slot = parseSlot(arg(args, 0, "remove <SLOT>"));
                printBuild(service.removePart(requireBuild(), slot), out);
            }
            case "check" -> printReport(service.checkCompatibility(requireBuild()), out);
            case "fits" -> {
                PartCategory slot = parseSlot(arg(args, 0, "fits <SLOT>"));
                printParts(service.compatibleParts(requireBuild(), slot), out);
            }
            case "plan" -> {
                String request = String.join(" ", args);
                if (request.isBlank()) {
                    throw new IllegalArgumentException("Usage: plan <what you want>, e.g. plan street bike under 2000, no brakes");
                }
                out.println("Planning... (the agent is searching and checking parts)");
                PlanResult result = service.planBuild(request, null);
                result.steps().forEach(s -> out.println("  > " + s));
                out.println();
                out.println(result.summary());
                out.println();
                printBuild(result.build(), out);
                if (result.aiAvailable()) {
                    out.println("  (" + result.tokensUsed() + " tokens used)");
                }
                currentBuildId = result.build().id();
                out.println("Build " + currentBuildId + " is now selected, so you can tweak it with add/remove.");
            }
            case "ask" -> {
                String question = String.join(" ", args);
                if (question.isBlank()) {
                    throw new IllegalArgumentException("Usage: ask <question>, e.g. ask mid or spanish bottom bracket?");
                }
                // the selected build (if any) is included so answers can refer to it
                AgentAnswer answer = service.askQuestion("cli", question, currentBuildId);
                answer.steps().forEach(s -> out.println("  > " + s));
                out.println(answer.answer());
                if (!answer.sources().isEmpty()) {
                    out.println("  Sources: " + String.join("; ", answer.sources()));
                }
            }
            case "recommend" -> {
                Map<String, String> o = options(args);
                RiderProfile profile = new RiderProfile(
                        (int) parseId(require(o, "height", "recommend --height 175 --spots street,skatepark --goals grind rails")).longValue(),
                        o.get("experience"),
                        o.containsKey("spots") ? Arrays.asList(o.get("spots").split("\\s*,\\s*")) : List.of(),
                        o.get("goals"),
                        o.containsKey("budget") ? parseMoney(o.get("budget")) : null,
                        o.containsKey("brakes"));
                StyleRecommendation r = service.recommendStyle(profile);
                if (r.note() != null) {
                    out.println("  " + r.note());
                }
                out.println("  Recommended frame top tube: " + r.topTube());
                for (StyleRecommendation.RankedStyle rs : r.ranked()) {
                    out.printf("  %-9s %3d  %s%n", rs.style(), rs.score(), rs.reason());
                }
                if (r.close()) {
                    out.println("  Your top two styles are close, so pick whichever sounds more fun.");
                }
                lastStyle = r.best();
                out.println("  Saved " + lastStyle + " as your style for 'optimize'." + (r.aiBased() ? "  (" + r.tokensUsed() + " tokens)" : ""));
            }
            case "prices" -> {
                Long partId = parseId(arg(args, 0, "prices <partId> [--refresh]"));
                PriceComparison c = service.comparePrices(partId, Arrays.asList(args).contains("--refresh"));
                out.println("  Prices for " + c.partName() + " (sample store data):");
                if (!c.hasQuotes()) {
                    out.println("  No store prices. Catalog estimate: " + money(c.estimate()));
                }
                for (PriceQuote q : c.quotes()) {
                    out.printf("  %-30s %12s + %10s shipping = %12s %s  (updated %s)%n", q.store(), money(q.price()),
                            money(q.shipping()), money(q.totalCad()), q.inStock() ? "" : "OUT OF STOCK", q.updatedAt());
                }
                c.unavailableSources().forEach(sname -> out.println("  " + sname + ": unavailable right now"));
            }
            case "buildprices" -> {
                BuildPriceSummary b = service.buildPrices(requireBuild());
                for (BuildPriceSummary.SlotPrice sp : b.slots()) {
                    out.printf("  %-15s %-32s %12s at %s%n", sp.slot(), sp.partName(),
                            sp.best() == null ? money(sp.msrp()) : money(sp.best().totalCad()),
                            sp.best() == null ? "MSRP (no store has it in stock)" : sp.best().store());
                }
                out.println("  MSRP total " + money(b.msrpTotal()) + ", cheapest total " + money(b.cheapestTotal())
                        + ", you save " + money(b.savings()));
            }
            case "optimize" -> {
                Map<String, String> o = options(args);
                BigDecimal target = parseMoney(arg(args, 0, "optimize <target> [--lock FRAME,FORK] [--style STREET] [--apply]"));
                List<PartCategory> locked = new ArrayList<>();
                if (o.containsKey("lock")) {
                    for (String slot : o.get("lock").split("\\s*,\\s*")) {
                        locked.add(parseSlot(slot));
                    }
                }
                RidingStyle style = o.containsKey("style") ? parseEnum(RidingStyle.class, o.get("style")) : lastStyle;
                OptimizationResult r = service.optimizeBudget(requireBuild(), target, locked, style);
                for (BudgetOptimizer.Swap sw : r.plan().swaps()) {
                    out.printf("  %-15s %s -> [%d] %s  (%s)%n", sw.slot(), sw.from().getName(), sw.to().getId(),
                            sw.to().getName(), sw.reason());
                }
                out.println("  " + r.plan().message());
                if (r.explanation() != null && !r.plan().swaps().isEmpty()) {
                    out.println();
                    out.println(r.explanation());
                }
                if (o.containsKey("apply") && !r.plan().swaps().isEmpty()) {
                    Map<PartCategory, Long> picks = new EnumMap<>(PartCategory.class);
                    r.plan().swaps().forEach(sw -> picks.put(sw.slot(), sw.to().getId()));
                    printBuild(service.applySwaps(requireBuild(), picks), out);
                } else if (!r.plan().swaps().isEmpty()) {
                    out.println("  Run the same command with --apply to make these swaps.");
                }
            }
            case "compare" -> {
                List<Long> ids = new ArrayList<>();
                for (String a : args) {
                    ids.add(parseId(a));
                }
                BuildComparison c = service.compareBuilds(ids);
                StringBuilder header = new StringBuilder(String.format("  %-15s", ""));
                c.builds().forEach(b -> header.append(String.format(" %-28s", "[" + b.id() + "] " + b.name())));
                out.println(header);
                for (BuildComparison.Row row : c.rows()) {
                    StringBuilder line = new StringBuilder(String.format("%s %-15s", row.differs() ? "*" : " ", row.slot()));
                    row.parts().forEach(n -> line.append(String.format(" %-28s", n == null ? "-" : n)));
                    out.println(line);
                }
                StringBuilder totals = new StringBuilder(String.format("  %-15s", "Total"));
                c.builds().forEach(b -> totals.append(String.format(" %-28s", money(b.totalPrice()) + ", " + b.compatibility().overall())));
                out.println(totals);
                out.println("  (* = different between builds)");
            }
            case "upgrade" -> {
                Map<String, String> o = options(args);
                BigDecimal budget = parseMoney(arg(args, 0, "upgrade <budget> [--style STREET] [--describe my bike...] [--apply-stage 1]"));
                RidingStyle style = o.containsKey("style") ? parseEnum(RidingStyle.class, o.get("style")) : lastStyle;
                UpgradeAdvisor.Plan plan = o.containsKey("describe")
                        ? service.planUpgrade(null, o.get("describe"), budget, style)
                        : service.planUpgrade(requireBuild(), null, budget, style);
                plan.steps().forEach(st -> out.println("  > " + st));
                plan.notes().forEach(n -> out.println("  Note: " + n));
                for (UpgradeAdvisor.Stage stage : plan.stages()) {
                    out.printf("  Stage %d (%s, compatibility %s): %s%n", stage.number(), money(stage.cost()),
                            stage.compatibility(), stage.reason());
                    for (UpgradeAdvisor.Option op : stage.options()) {
                        StringBuilder names = new StringBuilder();
                        op.parts().forEach(p -> names.append("[").append(p.getId()).append("] ").append(p.getName()).append("  "));
                        out.printf("    %-28s %s (%s)%n", op.slots().toString(), names.toString().trim(), op.reason());
                    }
                }
                out.println("  " + plan.summary());
                if (plan.buildId() != null) {
                    currentBuildId = plan.buildId();
                }
                if (o.containsKey("apply-stage") && plan.buildId() != null) {
                    int n = (int) parseId(o.get("apply-stage")).longValue();
                    UpgradeAdvisor.Stage stage = plan.stages().stream().filter(st -> st.number() == n).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("There's no stage " + n));
                    Map<PartCategory, Long> picks = new EnumMap<>(PartCategory.class);
                    stage.options().forEach(op -> op.parts().forEach(p -> picks.put(p.getCategory(), p.getId())));
                    printBuild(service.applySwaps(plan.buildId(), picks), out);
                }
            }
            case "rename" -> {
                String name = String.join(" ", args);
                printBuild(service.renameBuild(requireBuild(), name), out);
            }
            case "copy" -> {
                BuildView copy = service.duplicateBuild(requireBuild());
                currentBuildId = copy.id();
                out.println("Copied to build " + copy.id() + " (" + copy.name() + "). It's now selected.");
            }
            case "delete" -> {
                Long id = parseId(arg(args, 0, "delete <buildId>"));
                service.deleteBuild(id);
                if (id.equals(currentBuildId)) {
                    currentBuildId = null;
                }
                out.println("Deleted build " + id + ".");
            }
            default -> out.println("Unknown command '" + cmd + "'. Type 'help'.");
        }
    }

    // ---------- parsing ----------

    /** search [keyword words] [--category X] [--style X] [--min N] [--max N] */
    PartQuery parseQuery(String[] args) {
        List<String> keywords = new ArrayList<>();
        PartCategory category = null;
        RidingStyle style = null;
        BigDecimal min = null;
        BigDecimal max = null;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--")) {
                String value = arg(args, i + 1, a + " needs a value");
                i++;
                switch (a) {
                    case "--category" -> category = parseSlot(value);
                    case "--style" -> style = parseEnum(RidingStyle.class, value);
                    case "--min" -> min = parseMoney(value);
                    case "--max" -> max = parseMoney(value);
                    default -> throw new IllegalArgumentException("Unknown option " + a);
                }
            } else {
                keywords.add(a);
            }
        }
        String keyword = keywords.isEmpty() ? null : String.join(" ", keywords);
        return new PartQuery(keyword, category, min, max, style);
    }

    /** --key value words --flag  ->  {key: "value words", flag: ""} */
    Map<String, String> options(String[] args) {
        Map<String, String> out = new HashMap<>();
        String key = null;
        StringBuilder value = new StringBuilder();
        for (String a : args) {
            if (a.startsWith("--")) {
                if (key != null) {
                    out.put(key, value.toString().trim());
                }
                key = a.substring(2).toLowerCase();
                value.setLength(0);
            } else if (key != null) {
                value.append(a).append(' ');
            }
        }
        if (key != null) {
            out.put(key, value.toString().trim());
        }
        return out;
    }

    private String require(Map<String, String> options, String key, String usage) {
        String v = options.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
        return v;
    }

    private PartCategory parseSlot(String text) {
        return parseEnum(PartCategory.class, text);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String text) {
        try {
            return Enum.valueOf(type, text.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + text + "' isn't valid. Options: "
                    + Arrays.toString(type.getEnumConstants()));
        }
    }

    private Long parseId(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' isn't a number");
        }
    }

    private BigDecimal parseMoney(String text) {
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' isn't a price");
        }
    }

    private String arg(String[] args, int index, String usage) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
        return args[index];
    }

    private Long requireBuild() {
        if (currentBuildId == null) {
            throw new IllegalArgumentException("No build selected. Use 'new <name>' or 'use <buildId>' first.");
        }
        return currentBuildId;
    }

    // ---------- printing ----------

    private void printHelp(PrintStream out) {
        out.println("""
                  search [words] [--category FRAME] [--style STREET] [--min 50] [--max 450]
                  builds                     list saved builds
                  new <name>                 create a build and select it
                  use <buildId>              select an existing build
                  show                       show the selected build
                  add <SLOT> <partId>        put a part in a slot, e.g. add FRAME 1
                  remove <SLOT>              empty a slot
                  check                      run the compatibility checker
                  fits <SLOT>                list catalog parts that would fit a slot
                  plan <request>             AI plans a full build, e.g. plan street bike under 2000, no brakes
                  ask <question>             ask the BMX assistant, e.g. ask what's a freecoaster?
                  recommend --height <cm> --spots street,skatepark [--goals ...] [--experience BEGINNER] [--budget 1500] [--brakes]
                                             AI recommends a riding style and frame size
                  prices <partId> [--refresh] compare store prices for a part
                  buildprices                cheapest store for each part in the selected build
                  optimize <target> [--lock FRAME,FORK] [--style STREET] [--apply]
                                             cheaper swaps to hit a budget, explained by the AI
                  upgrade <budget> [--style STREET] [--describe my bike...] [--apply-stage 1]
                                             AI plans staged upgrades for the selected build or a described bike
                  compare <id> <id> [<id>]   compare two or three builds side by side
                  rename <name>              rename the selected build
                  copy                       duplicate the selected build
                  delete <buildId>           delete a build
                  quit""");
    }

    private void printParts(List<Part> parts, PrintStream out) {
        if (parts.isEmpty()) {
            out.println("  No parts match. Try removing a filter.");
            return;
        }
        for (Part p : parts) {
            out.printf("  [%d] %-32s %-15s %12s %7.0fg%n",
                    p.getId(), p.getName(), p.getCategory(), money(p.getPrice()), p.getWeight());
        }
    }

    private void printBuild(BuildView b, PrintStream out) {
        out.printf("Build %d: %s%n", b.id(), b.name());
        if (b.parts().isEmpty()) {
            out.println("  (no parts yet)");
        }
        for (Map.Entry<PartCategory, Part> e : b.parts().entrySet()) {
            Part p = e.getValue();
            out.printf("  %-15s [%d] %-32s %12s%n", e.getKey(), p.getId(), p.getName(), money(p.getPrice()));
        }
        out.printf("  Total: %s, %.0fg%n", money(b.totalPrice()), b.totalWeightGrams());
        out.println(b.complete() ? "  Complete!" : "  Missing: " + b.missingSlots());
        printReport(b.compatibility(), out);
    }

    private void printReport(CompatibilityReport report, PrintStream out) {
        out.println("  Compatibility: " + report.overall());
        for (RuleResult r : report.results()) {
            if (r.status() != Status.NOT_CHECKED) {
                out.printf("    %-8s %-22s %s%n", r.status(), r.rule(), r.message());
            }
        }
    }

    private String money(Money m) {
        return "$" + m.getAmount().setScale(2, RoundingMode.HALF_UP) + " " + m.getCurrency();
    }
}
