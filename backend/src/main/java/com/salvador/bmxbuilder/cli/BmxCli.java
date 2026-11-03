package com.salvador.bmxbuilder.cli;

import com.salvador.bmxbuilder.domain.Money;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import com.salvador.bmxbuilder.domain.PartQuery;
import com.salvador.bmxbuilder.domain.RidingStyle;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityReport;
import com.salvador.bmxbuilder.domain.compatibility.RuleResult;
import com.salvador.bmxbuilder.domain.compatibility.Status;
import com.salvador.bmxbuilder.service.BuildService;
import com.salvador.bmxbuilder.service.BuildView;
import com.salvador.bmxbuilder.service.NotFoundException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
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
