package com.salvador.bmxbuilder.agent;

import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/** Pulls the JSON object out of a model's reply (models sometimes add text around it). */
public final class JsonReplies {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private JsonReplies() {
    }

    /** The first {...} object in the text, or null if there isn't a readable one. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            Object parsed = JSON.readValue(text.substring(start, end + 1), Map.class);
            return (parsed instanceof Map) ? (Map<String, Object>) parsed : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
