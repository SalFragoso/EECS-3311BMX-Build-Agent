package com.salvador.bmxbuilder.agent;

import com.salvador.bmxbuilder.agent.llm.ChatMessage;
import com.salvador.bmxbuilder.agent.llm.LLMException;
import com.salvador.bmxbuilder.agent.llm.ToolSpec;
import com.salvador.bmxbuilder.agent.tools.AgentTool;
import com.salvador.bmxbuilder.agent.tools.ToolRegistry;
import com.salvador.bmxbuilder.agent.tools.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AgentOrchestratorTest {

    private ScriptedLLMClient llm;
    private ConversationMemory memory;
    private AgentOrchestrator agent;
    private final List<Map<String, Object>> echoCalls = new ArrayList<>();

    /** A simple tool that records its arguments and echoes them back. */
    private final AgentTool echo = new AgentTool() {
        public String name() { return "echo"; }
        public ToolSpec spec() { return new ToolSpec("echo", "Echoes its input", Map.of("type", "object")); }
        public ToolResult execute(Map<String, Object> args) {
            echoCalls.add(args);
            return ToolResult.ok("echo: " + args);
        }
    };

    private final AgentTool secret = new AgentTool() {
        public String name() { return "secret"; }
        public ToolSpec spec() { return new ToolSpec("secret", "Not for this task", Map.of("type", "object")); }
        public ToolResult execute(Map<String, Object> args) { return ToolResult.ok("should never run"); }
    };

    @BeforeEach
    void setUp() {
        llm = new ScriptedLLMClient();
        memory = new ConversationMemory();
        agent = new AgentOrchestrator(llm, new ToolRegistry(List.of(echo, secret)), memory);
    }

    private AgentTask task(String message, int maxSteps) {
        return new AgentTask("conv-1", "You are a test agent.", message, null, Set.of("echo"), maxSteps);
    }

    @Test
    void plainAnswerFinishesInOneStep() {
        llm.thenAnswer("Hello rider");

        AgentResult result = agent.run(task("hi", 5));

        assertTrue(result.finished());
        assertEquals("Hello rider", result.text());
        assertTrue(result.steps().isEmpty());
        assertEquals(50, result.tokensUsed());
    }

    @Test
    void toolResultsAreFedBackBeforeTheFinalAnswer() {
        llm.thenCall("echo", Map.of("x", 1)).thenAnswer("Done");

        AgentResult result = agent.run(task("use the tool", 5));

        assertEquals("Done", result.text());
        assertEquals(List.of(Map.of("x", 1)), echoCalls);
        assertEquals(1, result.steps().size());
        assertTrue(result.steps().get(0).startsWith("echo"));
        assertEquals(150, result.tokensUsed());

        // the second LLM call saw the tool request and its result
        List<ChatMessage> secondCall = llm.sentMessages.get(1);
        ChatMessage toolMessage = secondCall.get(secondCall.size() - 1);
        assertEquals(ChatMessage.Role.TOOL, toolMessage.role());
        assertEquals("call_1", toolMessage.toolCallId());
        assertTrue(toolMessage.content().contains("echo:"));
    }

    @Test
    void onlyTheTasksToolsAreOfferedOrRun() {
        llm.thenCall("secret", Map.of()).thenAnswer("ok");

        AgentResult result = agent.run(task("try the secret tool", 5));

        assertEquals(1, llm.sentTools.get(0).size());
        assertEquals("echo", llm.sentTools.get(0).get(0).name());
        assertTrue(result.steps().get(0).contains("isn't available for this task"));
    }

    @Test
    void stepLimitStopsAModelThatNeverFinishes() {
        AgentResult result = agent.run(task("loop forever", 3));   // no answer queued: model keeps calling tools

        assertFalse(result.finished());
        assertEquals(3, llm.sentMessages.size());
        assertTrue(result.text().contains("ran out of steps"));
    }

    @Test
    void followUpsSeeEarlierTurns() {
        llm.thenAnswer("A freecoaster lets you roll backwards.").thenAnswer("Yes, for street.");

        agent.run(task("what's a freecoaster?", 5));
        agent.run(task("should I get one?", 5));

        List<ChatMessage> second = llm.sentMessages.get(1);
        // system, remembered question, remembered answer, new question
        assertEquals(4, second.size());
        assertEquals("what's a freecoaster?", second.get(1).content());
        assertEquals("A freecoaster lets you roll backwards.", second.get(2).content());
    }

    @Test
    void contextIsSentButNotRemembered() {
        llm.thenAnswer("answer");
        AgentTask withContext = new AgentTask("conv-2", "sys", "question", "PASSAGES: lots of text", Set.of(), 5);

        agent.run(withContext);

        assertTrue(llm.sentMessages.get(0).get(1).content().contains("PASSAGES"));
        assertEquals("question", memory.recent("conv-2", 10).get(0).content());
    }

    @Test
    void llmFailureIsPassedToTheCaller() {
        llm.down();

        assertThrows(LLMException.class, () -> agent.run(task("hi", 5)));
    }

    @Test
    void memoryKeepsOnlyTheNewestMessages() {
        for (int i = 0; i < ConversationMemory.MAX_MESSAGES_PER_CONVERSATION + 5; i++) {
            memory.append("busy", ChatMessage.user("message " + i));
        }

        List<ChatMessage> all = memory.recent("busy", 100);
        assertEquals(ConversationMemory.MAX_MESSAGES_PER_CONVERSATION, all.size());
        assertEquals("message 5", all.get(0).content());
    }
}
