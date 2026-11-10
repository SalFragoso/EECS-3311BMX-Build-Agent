package com.salvador.bmxbuilder.web;

import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityReport;
import com.salvador.bmxbuilder.pricing.BuildPriceSummary;
import com.salvador.bmxbuilder.domain.RidingStyle;
import com.salvador.bmxbuilder.service.BuildComparison;
import com.salvador.bmxbuilder.service.BuildService;
import com.salvador.bmxbuilder.service.BuildView;
import com.salvador.bmxbuilder.service.OptimizationResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** REST API for the build configurator (F04) and compatibility checks (F05). */
@RestController
@RequestMapping("/api/builds")
public class BuildController {

    private final BuildService service;

    public BuildController(BuildService service) {
        this.service = service;
    }

    // POST /api/builds?name=Street%20build
    @PostMapping
    public ResponseEntity<BuildView> create(
            @RequestParam(name = "name", required = false) String name) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createBuild(name));
    }

    // GET /api/builds
    @GetMapping
    public List<BuildView> list() {
        return service.listBuilds();
    }

    // GET /api/builds/1
    @GetMapping("/{id}")
    public BuildView get(@PathVariable("id") Long id) {
        return service.viewBuild(id);
    }

    // PUT /api/builds/1/parts/FRAME?partId=1
    @PutMapping("/{id}/parts/{slot}")
    public BuildView selectPart(@PathVariable("id") Long id,
                                @PathVariable("slot") PartCategory slot,
                                @RequestParam("partId") Long partId) {
        return service.selectPart(id, slot, partId);
    }

    // DELETE /api/builds/1/parts/FRAME
    @DeleteMapping("/{id}/parts/{slot}")
    public BuildView removePart(@PathVariable("id") Long id,
                                @PathVariable("slot") PartCategory slot) {
        return service.removePart(id, slot);
    }

    // GET /api/builds/1/compatibility
    @GetMapping("/{id}/compatibility")
    public CompatibilityReport checkBuild(@PathVariable("id") Long id) {
        return service.checkCompatibility(id);
    }

    // GET /api/builds/1/compatible-parts?slot=BOTTOM_BRACKET
    @GetMapping("/{id}/compatible-parts")
    public List<Part> compatibleParts(@PathVariable("id") Long id,
                                      @RequestParam("slot") PartCategory slot) {
        return service.compatibleParts(id, slot);
    }

    // GET /api/builds/1/prices  (F06: cheapest store for each part)
    @GetMapping("/{id}/prices")
    public BuildPriceSummary prices(@PathVariable("id") Long id) {
        return service.buildPrices(id);
    }

    // ---------- F10: saved builds and comparison ----------

    // GET /api/builds/compare?ids=1,2
    @GetMapping("/compare")
    public BuildComparison compare(@RequestParam("ids") List<Long> ids) {
        return service.compareBuilds(ids);
    }

    // PATCH /api/builds/1?name=Street build v2
    @PatchMapping("/{id}")
    public BuildView rename(@PathVariable("id") Long id, @RequestParam("name") String name) {
        return service.renameBuild(id, name);
    }

    // POST /api/builds/1/duplicate
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<BuildView> duplicate(@PathVariable("id") Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.duplicateBuild(id));
    }

    // DELETE /api/builds/1
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        service.deleteBuild(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- F07: budget optimizer ----------

    // POST /api/builds/1/optimize?target=1500&locked=FRAME,FORK&style=STREET
    @PostMapping("/{id}/optimize")
    public OptimizationResult optimize(@PathVariable("id") Long id,
                                       @RequestParam("target") BigDecimal target,
                                       @RequestParam(name = "locked", required = false) List<PartCategory> locked,
                                       @RequestParam(name = "style", required = false) RidingStyle style) {
        return service.optimizeBudget(id, target, locked, style);
    }

    // POST /api/builds/1/apply-swaps?swaps=FORK:20,CRANKS:23
    @PostMapping("/{id}/apply-swaps")
    public BuildView applySwaps(@PathVariable("id") Long id, @RequestParam("swaps") String swaps) {
        Map<PartCategory, Long> selections = new EnumMap<>(PartCategory.class);
        for (String pair : swaps.split(",")) {
            String[] bits = pair.trim().split(":");
            if (bits.length != 2) {
                throw new IllegalArgumentException("Swaps look like SLOT:partId, e.g. FORK:20");
            }
            try {
                selections.put(PartCategory.valueOf(bits[0].trim().toUpperCase()), Long.parseLong(bits[1].trim()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + bits[1] + "' isn't a part id");
            }
        }
        return service.applySwaps(id, selections);
    }
}
