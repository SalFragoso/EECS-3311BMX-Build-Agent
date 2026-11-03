package com.salvador.bmxbuilder.data;

import com.salvador.bmxbuilder.domain.BikeBuild;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildRepository extends JpaRepository<BikeBuild, Long> {
}
