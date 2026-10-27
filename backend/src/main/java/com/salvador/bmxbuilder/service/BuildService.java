package com.salvador.bmxbuilder.service;
import org.springframework.stereotype.Service;

import com.salvador.bmxbuilder.data.PartRepository;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartQuery;

import java.util.List;

@Service
public class BuildService {

    private final PartRepository parts;

    public BuildService(PartRepository parts){
        this.parts = parts;
    }

    public List<Part> searchParts(PartQuery query){

        if (query.minPrice() != null && query.maxPrice() != null && query.minPrice().compareTo(query.maxPrice()) > 0){
            throw new IllegalArgumentException("Min price must be below max price.");
        }

        return parts.search(query);
    }
}
