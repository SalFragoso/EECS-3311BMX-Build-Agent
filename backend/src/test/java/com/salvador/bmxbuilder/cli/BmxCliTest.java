package com.salvador.bmxbuilder.cli;

import com.salvador.bmxbuilder.domain.Money;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import com.salvador.bmxbuilder.domain.PartQuery;
import com.salvador.bmxbuilder.domain.RidingStyle;
import com.salvador.bmxbuilder.service.BuildService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BmxCliTest {

    private BuildService service;
    private BmxCli cli;

    @BeforeEach
    void setUp() {
        service = mock(BuildService.class);   // fake service: these tests only check the CLI itself
        cli = new BmxCli(service);
    }

    /** Feeds the given lines to the CLI and returns everything it printed. */
    private String runWith(String input) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        cli.repl(new Scanner(input), new PrintStream(bytes, true));
        return bytes.toString();
    }

    @Test
    void searchPrintsMatchingParts() {
        Part frame = new Part("Cult", "Hawk", PartCategory.FRAME,
                new Money(new BigDecimal("449.99"), "CAD"), 2150, "frame.png");
        when(service.searchParts(any())).thenReturn(List.of(frame));

        String output = runWith("search cult\nquit\n");

        assertTrue(output.contains("Cult Hawk"));
        assertTrue(output.contains("$449.99 CAD"));
    }

    @Test
    void searchOptionsBecomeAPartQuery() {
        PartQuery q = cli.parseQuery(new String[]{"odyssey", "--category", "fork", "--max", "200", "--style", "street"});

        assertEquals("odyssey", q.keyword());
        assertEquals(PartCategory.FORK, q.category());
        assertEquals(0, q.maxPrice().compareTo(new BigDecimal("200")));
        assertEquals(RidingStyle.STREET, q.style());
        assertNull(q.minPrice());
    }

    @Test
    void addWithoutABuildSelectedGivesAFriendlyError() {
        String output = runWith("add FRAME 1\nquit\n");

        assertTrue(output.contains("No build selected"));
        verify(service, never()).selectPart(any(), any(), any());
    }

    @Test
    void badSlotNameListsTheValidOptions() {
        String output = runWith("search --category frog\nquit\n");

        assertTrue(output.contains("'frog' isn't valid"));
        assertTrue(output.contains("FRAME"));
    }

    @Test
    void unknownCommandDoesNotCrash() {
        String output = runWith("wheelie\nquit\n");

        assertTrue(output.contains("Unknown command 'wheelie'"));
        assertTrue(output.contains("Bye!"));
    }
}
