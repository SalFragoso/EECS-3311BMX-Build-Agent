package com.salvador.bmxbuilder.pricing;

import com.salvador.bmxbuilder.domain.BikeBuild;
import com.salvador.bmxbuilder.domain.Money;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** F06: asks every store for prices, sorts them, and totals a build at its cheapest. */
@Service
public class PriceService {

    private final List<PriceSource> sources;

    public PriceService(List<PriceSource> sources) {
        this.sources = List.copyOf(sources);
    }

    public PriceComparison compare(Part part, boolean refresh) {
        List<PriceQuote> quotes = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        for (PriceSource source : sources) {
            try {
                if (refresh) {
                    source.refresh(part.getId());
                }
                quotes.addAll(source.quotes(part));
            } catch (RuntimeException e) {
                // one broken store shouldn't hide the others (F06 error case)
                unavailable.add(source.name());
            }
        }
        quotes.sort(Comparator.comparing((PriceQuote q) -> !q.inStock())   // in stock first
                .thenComparing(q -> q.totalCad().getAmount()));
        return new PriceComparison(part.getId(), part.getName(), quotes, unavailable, part.getPrice());
    }

    public BuildPriceSummary cheapestBuild(BikeBuild build) {
        List<BuildPriceSummary.SlotPrice> slots = new ArrayList<>();
        Money msrpTotal = new Money(BigDecimal.ZERO, "CAD");
        Money cheapestTotal = new Money(BigDecimal.ZERO, "CAD");

        for (Map.Entry<PartCategory, Part> e : build.getSlots().entrySet()) {
            Part part = e.getValue();
            PriceQuote best = compare(part, false).quotes().stream()
                    .filter(PriceQuote::inStock)
                    .findFirst()
                    .orElse(null);
            slots.add(new BuildPriceSummary.SlotPrice(e.getKey(), part.getId(), part.getName(), part.getPrice(), best));
            msrpTotal = msrpTotal.add(part.getPrice());
            cheapestTotal = cheapestTotal.add(best != null ? best.totalCad() : part.getPrice());
        }
        Money savings = new Money(msrpTotal.getAmount().subtract(cheapestTotal.getAmount()), "CAD");
        return new BuildPriceSummary(build.getId(), slots, msrpTotal, cheapestTotal, savings);
    }
}
