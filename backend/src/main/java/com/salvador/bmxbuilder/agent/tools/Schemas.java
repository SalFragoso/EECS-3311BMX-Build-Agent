package com.salvador.bmxbuilder.agent.tools;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small helpers for writing the JSON Schemas that describe tool arguments to the LLM. */
final class Schemas {

    private Schemas() {
    }

    static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    static Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    static Map<String, Object> number(String description) {
        return Map.of("type", "number", "description", description);
    }

    static Map<String, Object> integer(String description) {
        return Map.of("type", "integer", "description", description);
    }

    static Map<String, Object> enumOf(Class<? extends Enum<?>> type, String description) {
        List<String> values = Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
        return Map.of("type", "string", "enum", values, "description", description);
    }

    /** LinkedHashMap keeps the properties in the order written, which reads better for the model. */
    static Map<String, Object> props(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
