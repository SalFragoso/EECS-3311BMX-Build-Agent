package com.salvador.bmxbuilder.agent.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapter pattern: the Adapter. Turns our ChatMessage/ToolSpec objects into Groq's
 * OpenAI-style chat completions JSON, and Groq's reply back into an LLMResponse.
 * The Groq HTTP API is the Adaptee; AgentOrchestrator is the Client.
 */
@Component
public class GroqClient implements LLMClient {

    private static final String BASE_URL = "https://api.groq.com/openai/v1";

    private final String apiKey;
    private final String model;
    private final RestClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    public GroqClient(@Value("${groq.api-key:}") String apiKey,
                      @Value("${groq.model:llama-3.3-70b-versatile}") String model) {
        this(apiKey, model, RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(timeouts())
                .build());
    }

    GroqClient(String apiKey, String model, RestClient http) {
        this.apiKey = apiKey;
        this.model = model;
        this.http = http;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return factory;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public LLMResponse complete(List<ChatMessage> messages, List<ToolSpec> tools) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LLMException("GROQ_API_KEY is not set. Add it to backend/.env (see .env.example).");
        }
        String requestJson = json.writeValueAsString(toRequest(messages, tools));
        String responseJson = send(requestJson);
        return fromResponse(json.readValue(responseJson, GroqResponse.class));
    }

    private String send(String requestJson) {
        try {
            return http.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429) {
                throw new LLMException("Groq rate limit reached. Wait a minute and try again.", e);
            }
            if (status == 401) {
                throw new LLMException("Groq rejected the API key (401). Check GROQ_API_KEY in backend/.env.", e);
            }
            throw new LLMException("Groq returned " + status + ": " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new LLMException("Couldn't reach Groq: " + e.getMessage(), e);
        }
    }

    // ---------- mapping: our types -> Groq JSON ----------

    GroqRequest toRequest(List<ChatMessage> messages, List<ToolSpec> tools) {
        List<GroqMessage> out = new ArrayList<>();
        for (ChatMessage m : messages) {
            List<GroqToolCall> calls = null;
            if (!m.toolCalls().isEmpty()) {
                calls = new ArrayList<>();
                for (ToolCall c : m.toolCalls()) {
                    calls.add(new GroqToolCall(c.id(), "function", new GroqFunctionCall(c.name(), c.rawArguments())));
                }
            }
            out.add(new GroqMessage(m.role().name().toLowerCase(), m.content(), calls, m.toolCallId()));
        }

        List<GroqTool> groqTools = null;
        String toolChoice = null;
        if (tools != null && !tools.isEmpty()) {
            groqTools = new ArrayList<>();
            for (ToolSpec t : tools) {
                groqTools.add(new GroqTool("function", new GroqFunction(t.name(), t.description(), t.parameters())));
            }
            toolChoice = "auto";
        }
        return new GroqRequest(model, out, groqTools, toolChoice, 0.2);
    }

    // ---------- mapping: Groq JSON -> our types ----------

    LLMResponse fromResponse(GroqResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new LLMException("Groq sent back an empty response");
        }
        GroqMessage message = response.choices().get(0).message();
        int tokens = (response.usage() == null) ? 0 : response.usage().totalTokens();

        List<ToolCall> calls = new ArrayList<>();
        if (message.toolCalls() != null) {
            for (GroqToolCall c : message.toolCalls()) {
                String raw = (c.function().arguments() == null) ? "{}" : c.function().arguments();
                calls.add(new ToolCall(c.id(), c.function().name(), parseArguments(raw), raw));
            }
        }
        return new LLMResponse(message.content(), calls, tokens);
    }

    /** The model sends tool arguments as a JSON string. Bad JSON becomes an empty map, so the tool reports what's missing. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String raw) {
        try {
            Object parsed = json.readValue(raw, Map.class);
            return (parsed instanceof Map) ? (Map<String, Object>) parsed : Map.of();
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    // ---------- Groq's JSON shapes (OpenAI-compatible) ----------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GroqRequest(String model,
                       List<GroqMessage> messages,
                       List<GroqTool> tools,
                       @JsonProperty("tool_choice") String toolChoice,
                       Double temperature) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroqMessage(String role,
                       String content,
                       @JsonProperty("tool_calls") List<GroqToolCall> toolCalls,
                       @JsonProperty("tool_call_id") String toolCallId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroqToolCall(String id, String type, GroqFunctionCall function) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroqFunctionCall(String name, String arguments) {
    }

    public record GroqTool(String type, GroqFunction function) {
    }

    public record GroqFunction(String name, String description, Map<String, Object> parameters) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroqResponse(List<GroqChoice> choices, GroqUsage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroqChoice(GroqMessage message, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroqUsage(@JsonProperty("total_tokens") int totalTokens) {
    }
}
