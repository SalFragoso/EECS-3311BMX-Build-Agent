package com.salvador.bmxbuilder.web;

import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartQuery;
import com.salvador.bmxbuilder.pricing.PriceComparison;
import com.salvador.bmxbuilder.service.BuildService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** REST API for the parts catalog (F03). Errors are handled by ApiExceptionHandler. */
@RestController
@RequestMapping("/api/parts")
public class CatalogController {

    private final BuildService service;

    public CatalogController(BuildService service) {
        this.service = service;
    }

    // GET /api/parts?keyword=odyssey&category=FORK&minPrice=100&maxPrice=300&style=STREET
    @GetMapping
    public List<Part> search(PartQuery query) {
        return service.searchParts(query);
    }

    // GET /api/parts/1
    @GetMapping("/{id}")
    public Part get(@PathVariable("id") Long id) {
        return service.getPart(id);
    }

    // GET /api/parts/1/prices?refresh=true  (F06)
    @GetMapping("/{id}/prices")
    public PriceComparison prices(@PathVariable("id") Long id,
                                  @RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {
        return service.comparePrices(id, refresh);
    }
}
