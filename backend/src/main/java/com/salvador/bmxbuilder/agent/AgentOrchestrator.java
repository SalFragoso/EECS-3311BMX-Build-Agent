package com.salvador.bmxbuilder.agent;

import com.salvador.bmxbuilder.agent.llm.ChatMessage;
import com.salvador.bmxbuilder.agent.llm.LLMClient;
import com.salvador.bmxbuilder.agent.llm.LLMResponse;
import com.salvador.bmxbuilder.agent.llm.ToolCall;
import com.salvador.bmxbuilder.agent.llm.ToolSpec;
import com.salvador.bmxbuilder.agent.tools.ToolRegistry;
import com.salvador.bmxbuilder.agent.tools.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The agent loop:
 *   1. send the conversation + available tools to the LLM
 *   2. if it asks for tools, run them and add the results to the conversation, then go to 1
 *   3. if it answers in plain text, that's the final answer
 * It stops after task.maxSteps() LLM calls so a confused model can't loop forever.
 * The LLM only decides; every action goes through a deterministic tool.
 */
@Component
public class AgentOrchestrator {

    static final int MEMORY_MESSAGES = 10;
    /** Tool results older than the newest few are shortened, so each request stays small (Groq's free tier counts tokens per minute). */
    static final int FULL_TOOL_RESULTS = 3;
    static final int SHORT_RESULT_CHARS = 160;

    private final LLMClient llm;
    private final ToolRegistry tools;
    private final ConversationMemory memory;

    public AgentOrchestrator(LLMClient llm, ToolRegistry tools, ConversationMemory memory) {
        this.llm = llm;
        this.tools = tools;
        this.memory = memory;
    }

    /** Throws LLMException if the model can't be reached; callers decide the fallback. */
    public AgentResult run(AgentTask task) {
        List<ChatMessage> messages = buildMessages(task);
        List<ToolSpec> specs = tools.specs(task.toolNames());
        List<String> steps = new ArrayList<>();
        int tokens = 0;

        for (int step = 0; step < task.maxSteps(); step++) {
            LLMResponse response = llm.complete(compact(messages), specs);
            tokens += response.tokensUsed();

            if (!response.hasToolCalls()) {
                String answer = (response.text() == null) ? "" : response.text().trim();
                remember(task, answer);
                return new AgentResult(answer, steps, true, tokens);
            }

            messages.add(ChatMessage.assistantToolCalls(response.text(), response.toolCalls()));
            messages.addAll(handleToolCalls(task, response.toolCalls(), steps));
        }

        String stopped = "I ran out of steps before finishing, so this is only partly done. "
                + "Check the result and try again with a more specific request.";
        remember(task, stopped);
        return new AgentResult(stopped, steps, false, tokens);
    }

    private List<ChatMessage> buildMessages(AgentTask task) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(task.systemPrompt()));
        messages.addAll(memory.recent(task.conversationId(), MEMORY_MESSAGES));
        String userText = (task.context() == null || task.context().isBlank())
                ? task.userMessage()
                : task.userMessage() + "\n\n" + task.context();
        messages.add(ChatMessage.user(userText));
        return messages;
    }

    /**
     * What the model is sent: the full conversation, but tool results older than the newest
     * FULL_TOOL_RESULTS cut down to their first line. The latest build state is always in a recent result.
     */
    static List<ChatMessage> compact(List<ChatMessage> messages) {
        int toolResults = 0;
        for (ChatMessage m : messages) {
            if (m.role() == ChatMessage.Role.TOOL) {
                toolResults++;
            }
        }
        int toShorten = toolResults - FULL_TOOL_RESULTS;
        if (toShorten <= 0) {
            return messages;
        }
        List<ChatMessage> out = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            if (m.role() == ChatMessage.Role.TOOL && toShorten > 0) {
                toShorten--;
                out.add(ChatMessage.tool(m.toolCallId(), shorten(m.content())));
            } else {
                out.add(m);
            }
        }
        return out;
    }

    private static String shorten(String content) {
        if (content == null) {
            return "";
        }
        String first = content.lines().findFirst().orElse("");
        if (first.length() > SHORT_RESULT_CHARS) {
            first = first.substring(0, SHORT_RESULT_CHARS);
        }
        return first.length() == content.length() ? content : first + " ... (older result shortened)";
    }

    /** Only tools the task allows can run, even if the model names another one. */
    private List<ChatMessage> handleToolCalls(AgentTask task, List<ToolCall> calls, List<String> steps) {
        List<ChatMessage> results = new ArrayList<>();
        for (ToolCall call : calls) {
            ToolResult result = task.toolNames().contains(call.name())
                    ? tools.execute(call)
                    : ToolResult.error("Tool '" + call.name() + "' isn't available for this task");
            steps.add(call.name() + " " + call.arguments() + " -> "
                    + (result.success() ? "ok" : "error: " + result.content()));
            results.add(ChatMessage.tool(call.id(), result.toMessageContent()));
        }
        return results;
    }

    private void remember(AgentTask task, String answer) {
        memory.append(task.conversationId(), ChatMessage.user(task.userMessage()));
        memory.append(task.conversationId(), ChatMessage.assistant(answer));
    }
}
