package com.salvador.bmxbuilder.agent.tools;

import com.salvador.bmxbuilder.agent.llm.ToolSpec;
import com.salvador.bmxbuilder.data.PartRepository;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import com.salvador.bmxbuilder.domain.PartQuery;
import com.salvador.bmxbuilder.domain.RidingStyle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Lets the agent search the parts catalog with the same filters as F03. */
@Component
public class SearchCatalogTool implements AgentTool {

    static final int MAX_RESULTS = 10;

    private final PartRepository parts;

    public SearchCatalogTool(PartRepository parts) {
        this.parts = parts;
    }

    @Override
    public String name() {
        return "search_catalog";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(),
                "Search the BMX parts catalog. Returns up to " + MAX_RESULTS + " parts with their id, price, "
                        + "weight, riding styles and specs. Only use part ids that this tool returns.",
                Schemas.object(Schemas.props(
                        "category", Schemas.enumOf(PartCategory.class, "Which slot/kind of part"),
                        "keyword", Schemas.string("Words to match in the brand or model name"),
                        "max_price", Schemas.number("Highest price in CAD"),
                        "min_price", Schemas.number("Lowest price in CAD"),
                        "style", Schemas.enumOf(RidingStyle.class, "Only parts suited to this riding style")),
                        List.of()));
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        BigDecimal min = ToolArgs.optionalDecimal(args, "min_price");
        BigDecimal max = ToolArgs.optionalDecimal(args, "max_price");
        if (min != null && max != null && min.compareTo(max) > 0) {
            return ToolResult.error("min_price can't be more than max_price");
        }
        PartQuery query = new PartQuery(
                ToolArgs.optionalString(args, "keyword"),
                ToolArgs.optionalEnum(args, "category", PartCategory.class),
                min, max,
                ToolArgs.optionalEnum(args, "style", RidingStyle.class));

        List<Part> found = parts.search(query);
        if (found.isEmpty()) {
            return ToolResult.ok("No parts matched. Try removing a filter, like the style or price limit.");
        }
        String lines = found.stream().limit(MAX_RESULTS).map(BuildSummary::part).collect(Collectors.joining("\n"));
        String more = found.size() > MAX_RESULTS
                ? "\n(" + (found.size() - MAX_RESULTS) + " more; narrow the search to see them)"
                : "";
        return ToolResult.ok(lines + more);
    }
}
