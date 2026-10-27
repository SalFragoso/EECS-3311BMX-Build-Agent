package com.salvador.bmxbuilder.web;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartQuery;
import com.salvador.bmxbuilder.service.BuildService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/parts")
public class CatalogController {

    private final BuildService service;

    public CatalogController(BuildService service) {

        this.service = service;
    }

    @GetMapping
    public List<Part> search(PartQuery query){
        return service.searchParts(query);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
