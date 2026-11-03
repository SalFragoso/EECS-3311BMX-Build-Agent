package com.salvador.bmxbuilder.web;

import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityReport;
import com.salvador.bmxbuilder.service.BuildService;
import com.salvador.bmxbuilder.service.BuildView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}
