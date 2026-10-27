package com.salvador.bmxbuilder.data;

import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import com.salvador.bmxbuilder.domain.PartQuery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartRepository extends JpaRepository<Part, Long>{

    List<Part> findByCategory(PartCategory category);

    default List<Part> search(PartQuery q) {

        return findAll().stream().filter(q::matches).toList();
    }
}

