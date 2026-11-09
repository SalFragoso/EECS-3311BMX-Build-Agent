package com.salvador.bmxbuilder.agent;

import com.salvador.bmxbuilder.agent.llm.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Short-term memory: the last few user/assistant messages per conversation, so follow-ups like
 * "what about for park?" make sense. Kept in memory only (cleared when the app restarts).
 * Tool calls aren't stored, just the question and the final answer, to keep prompts small.
 */
@Component
public class ConversationMemory {

    static final int MAX_MESSAGES_PER_CONVERSATION = 20;

    private final Map<String, Deque<ChatMessage>> history = new HashMap<>();

    public synchronized void append(String conversationId, ChatMessage message) {
        Deque<ChatMessage> messages = history.computeIfAbsent(conversationId, id -> new ArrayDeque<>());
        messages.addLast(message);
        while (messages.size() > MAX_MESSAGES_PER_CONVERSATION) {
            messages.removeFirst();
        }
    }

    /** The newest n messages, oldest first. */
    public synchronized List<ChatMessage> recent(String conversationId, int n) {
        List<ChatMessage> all = new ArrayList<>(history.getOrDefault(conversationId, new ArrayDeque<>()));
        return all.subList(Math.max(0, all.size() - n), all.size());
    }

    public synchronized void clear(String conversationId) {
        history.remove(conversationId);
    }
}
