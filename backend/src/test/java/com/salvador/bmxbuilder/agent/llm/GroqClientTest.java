package com.salvador.bmxbuilder.agent.llm;

import com.salvador.bmxbuilder.agent.llm.GroqClient.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the adapter's translation both ways. No network: the HTTP client is never used here. */
class GroqClientTest {

    private final GroqClient client = new GroqClient("test-key", "test-model", null);

    @Test
    void missingKeyGivesAClearError() {
        GroqClient noKey = new GroqClient("", "test-model", null);

        LLMException e = assertThrows(LLMException.class,
                () -> noKey.complete(List.of(ChatMessage.user("hi")), List.of()));
        assertTrue(e.getMessage().contains("GROQ_API_KEY"));
    }

    @Test
    void requestMapsRolesAndTools() {
        ToolSpec spec = new ToolSpec("search_catalog", "Search parts", Map.of("type", "object"));

        GroqRequest req = client.toRequest(
                List.of(ChatMessage.system("be helpful"), ChatMessage.user("street bike")), List.of(spec));

        assertEquals("test-model", req.model());
        assertEquals("system", req.messages().get(0).role());
        assertEquals("user", req.messages().get(1).role());
        assertEquals("search_catalog", req.tools().get(0).function().name());
        assertEquals("auto", req.toolChoice());
    }

    @Test
    void noToolsMeansNoToolFieldsAtAll() {
        GroqRequest req = client.toRequest(List.of(ChatMessage.user("hi")), List.of());

        assertNull(req.tools());
        assertNull(req.toolChoice());
    }

    @Test
    void toolCallsAndResultsAreEchoedBack() {
        ToolCall call = new ToolCall("call_1", "search_catalog", Map.of("keyword", "cult"), "{\"keyword\":\"cult\"}");

        GroqRequest req = client.toRequest(List.of(
                ChatMessage.assistantToolCalls(null, List.of(call)),
                ChatMessage.tool("call_1", "id=1 | Cult Hawk")), List.of());

        GroqMessage assistant = req.messages().get(0);
        assertEquals("assistant", assistant.role());
        assertEquals("{\"keyword\":\"cult\"}", assistant.toolCalls().get(0).function().arguments());
        GroqMessage toolResult = req.messages().get(1);
        assertEquals("tool", toolResult.role());
        assertEquals("call_1", toolResult.toolCallId());
    }

    @Test
    void responseTextAndTokensAreRead() {
        GroqResponse response = new GroqResponse(
                List.of(new GroqChoice(new GroqMessage("assistant", "Here's your build", null, null), "stop")),
                new GroqUsage(321));

        LLMResponse r = client.fromResponse(response);

        assertEquals("Here's your build", r.text());
        assertFalse(r.hasToolCalls());
        assertEquals(321, r.tokensUsed());
    }

    @Test
    void toolCallArgumentsAreParsed() {
        GroqToolCall call = new GroqToolCall("call_9", "function",
                new GroqFunctionCall("search_catalog", "{\"category\":\"FRAME\",\"max_price\":450}"));
        GroqResponse response = new GroqResponse(
                List.of(new GroqChoice(new GroqMessage("assistant", null, List.of(call), null), "tool_calls")),
                null);

        LLMResponse r = client.fromResponse(response);

        assertTrue(r.hasToolCalls());
        ToolCall parsed = r.toolCalls().get(0);
        assertEquals("call_9", parsed.id());
        assertEquals("FRAME", parsed.arguments().get("category"));
        assertEquals(450, ((Number) parsed.arguments().get("max_price")).intValue());
        assertEquals(0, r.tokensUsed());   // no usage block -> 0, not a crash
    }

    @Test
    void brokenArgumentJsonBecomesEmptyArguments() {
        GroqToolCall call = new GroqToolCall("call_2", "function", new GroqFunctionCall("search_catalog", "{oops"));
        GroqResponse response = new GroqResponse(
                List.of(new GroqChoice(new GroqMessage("assistant", null, List.of(call), null), "tool_calls")),
                null);

        assertTrue(client.fromResponse(response).toolCalls().get(0).arguments().isEmpty());
    }

    @Test
    void emptyResponseIsAnError() {
        assertThrows(LLMException.class, () -> client.fromResponse(new GroqResponse(List.of(), null)));
    }
}
