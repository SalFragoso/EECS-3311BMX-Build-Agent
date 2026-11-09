package com.salvador.bmxbuilder.agent.tools;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Reads tool arguments from the map the LLM sent. Models aren't always consistent
 * (a number may arrive as 450 or "450", an enum as "frame" or "FRAME"), so this is forgiving,
 * and it throws IllegalArgumentException with a clear message the model can act on.
 */
final class ToolArgs {

    private ToolArgs() {
    }

    static String optionalString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    static String requiredString(Map<String, Object> args, String key) {
        String text = optionalString(args, key);
        if (text == null) {
            throw new IllegalArgumentException("Missing required argument '" + key + "'");
        }
        return text;
    }

    static long requiredLong(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof Number n) {
            return n.longValue();
        }
        String text = requiredString(args, key);
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + key + "' must be a whole number, got '" + text + "'");
        }
    }

    static BigDecimal optionalDecimal(Map<String, Object> args, String key) {
        String text = optionalString(args, key);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + key + "' must be a number, got '" + text + "'");
        }
    }

    static <E extends Enum<E>> E optionalEnum(Map<String, Object> args, String key, Class<E> type) {
        String text = optionalString(args, key);
        if (text == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, text.toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + text + "' isn't a valid " + key + ". Options: "
                    + java.util.Arrays.toString(type.getEnumConstants()));
        }
    }

    static <E extends Enum<E>> E requiredEnum(Map<String, Object> args, String key, Class<E> type) {
        E value = optionalEnum(args, key, type);
        if (value == null) {
            throw new IllegalArgumentException("Missing required argument '" + key + "'");
        }
        return value;
    }
}
