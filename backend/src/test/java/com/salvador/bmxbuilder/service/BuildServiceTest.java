package com.salvador.bmxbuilder.service;

import com.salvador.bmxbuilder.data.PartRepository;
import com.salvador.bmxbuilder.domain.PartQuery;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class BuildServiceTest {

    @Test
    void searchRejectsMinPriceAboveMaxPrice() {
        PartRepository repo = mock(PartRepository.class);
        BuildService service = new BuildService(repo);
        PartQuery q = new PartQuery(null, null, new BigDecimal("500"), new BigDecimal("100"), null);

        assertThrows(IllegalArgumentException.class, () -> service.searchParts(q));
        verify(repo, never()).search(any());
    }
}