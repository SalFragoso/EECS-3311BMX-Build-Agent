package com.salvador.bmxbuilder.service;

import com.salvador.bmxbuilder.data.BuildRepository;
import com.salvador.bmxbuilder.data.PartRepository;
import com.salvador.bmxbuilder.domain.*;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityChecker;
import com.salvador.bmxbuilder.domain.compatibility.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BuildServiceTest {

    private PartRepository partRepo;
    private BuildRepository buildRepo;
    private BuildService service;

    @BeforeEach
    void setUp() {
        // Fake repositories, so no database is needed. The checker is the real one.
        partRepo = mock(PartRepository.class);
        buildRepo = mock(BuildRepository.class);
        service = new BuildService(partRepo, buildRepo, new CompatibilityChecker());

        // save() hands back whatever it was given, like the real repository does
        when(buildRepo.save(any(BikeBuild.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Part part(PartCategory category, Map<String, String> specs) {
        Part p = new Part("Test", category.name(), category,
                new Money(new BigDecimal("50"), "CAD"), 100, "test.png");
        p.getSpecs().putAll(specs);
        return p;
    }

    @Test
    void searchRejectsMinPriceAboveMaxPrice() {
        PartQuery q = new PartQuery(null, null, new BigDecimal("500"), new BigDecimal("100"), null);

        assertThrows(IllegalArgumentException.class, () -> service.searchParts(q));
        verify(partRepo, never()).search(any());
    }

    @Test
    void blankNameBecomesUntitled() {
        BuildView view = service.createBuild("   ");

        assertEquals("Untitled build", view.name());
    }

    @Test
    void selectPartSavesTheBuildAndReturnsTotals() {
        BikeBuild build = new BikeBuild("Street");
        Part frame = part(PartCategory.FRAME, Map.of("bbType", "MID"));
        when(buildRepo.findById(1L)).thenReturn(Optional.of(build));
        when(partRepo.findById(7L)).thenReturn(Optional.of(frame));

        BuildView view = service.selectPart(1L, PartCategory.FRAME, 7L);

        assertSame(frame, view.parts().get(PartCategory.FRAME));
        assertEquals(0, view.totalPrice().getAmount().compareTo(new BigDecimal("50")));
        verify(buildRepo).save(build);
    }

    @Test
    void selectPartFlagsAMisfitButStillAddsIt() {
        BikeBuild build = new BikeBuild("Mismatch");
        build.setPart(PartCategory.FRAME, part(PartCategory.FRAME, Map.of("bbType", "MID")));
        Part spanishBb = part(PartCategory.BOTTOM_BRACKET, Map.of("bbType", "SPANISH"));
        when(buildRepo.findById(1L)).thenReturn(Optional.of(build));
        when(partRepo.findById(8L)).thenReturn(Optional.of(spanishBb));

        BuildView view = service.selectPart(1L, PartCategory.BOTTOM_BRACKET, 8L);

        assertTrue(view.parts().containsKey(PartCategory.BOTTOM_BRACKET));
        assertEquals(Status.FAIL, view.compatibility().overall());
    }

    @Test
    void unknownBuildThrowsNotFound() {
        when(buildRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.selectPart(99L, PartCategory.FRAME, 1L));
    }

    @Test
    void partInTheWrongSlotIsRejectedAndNotSaved() {
        BikeBuild build = new BikeBuild("Wrong slot");
        when(buildRepo.findById(1L)).thenReturn(Optional.of(build));
        when(partRepo.findById(4L)).thenReturn(Optional.of(part(PartCategory.FORK, Map.of())));

        assertThrows(IllegalArgumentException.class, () -> service.selectPart(1L, PartCategory.FRAME, 4L));
        verify(buildRepo, never()).save(any());
    }

    @Test
    void compatiblePartsLeavesOutMisfits() {
        BikeBuild build = new BikeBuild("Mid frame");
        build.setPart(PartCategory.FRAME, part(PartCategory.FRAME, Map.of("bbType", "MID")));
        Part midBb = part(PartCategory.BOTTOM_BRACKET, Map.of("bbType", "MID"));
        Part spanishBb = part(PartCategory.BOTTOM_BRACKET, Map.of("bbType", "SPANISH"));
        when(buildRepo.findById(1L)).thenReturn(Optional.of(build));
        when(partRepo.findByCategory(PartCategory.BOTTOM_BRACKET)).thenReturn(List.of(midBb, spanishBb));

        List<Part> fits = service.compatibleParts(1L, PartCategory.BOTTOM_BRACKET);

        assertEquals(List.of(midBb), fits);
    }
}
