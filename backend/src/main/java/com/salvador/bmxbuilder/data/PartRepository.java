package com.salvador.bmxbuilder.data;

import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import com.salvador.bmxbuilder.domain.PartQuery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartRepository extends JpaRepository<Part, Long>{

    List<Part> findByCategory(PartCategory category);

    default List<Part> search(PartQuery q){
        return findAll().stream()
                .filter(p -> q.keyword() == null || p.getBrand().toLowerCase().contains(q.keyword().toLowerCase())
                        || p.getModel().toLowerCase().contains(q.keyword().toLowerCase())/* brand or model contains the keyword */)
                .filter(p -> q.category() == null || p.getCategory().equals(q.category())/* category matches */)
                .filter(p -> q.minPrice() == null || p.getPrice().getAmount().compareTo(q.minPrice()) >= 0/* price >= minPrice */)
                .filter(p -> q.maxPrice() == null || p.getPrice().getAmount().compareTo(q.maxPrice()) <= 0/* price <= maxPrice */)
                .filter(p -> q.style() == null || p.getStyleTags().contains(q.style())/* part's styleTags contain the style */)
                .toList();
    }
}

