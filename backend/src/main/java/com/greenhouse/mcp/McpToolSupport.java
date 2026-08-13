package com.greenhouse.mcp;

import com.greenhouse.common.DomainValidationException;
import com.greenhouse.crop.CropNotFoundException;
import com.greenhouse.goal.GoalNotFoundException;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// Shared plumbing for every MCP tool handler in this package: maps known domain
// exceptions to a clean, LLM-readable error message (e.g. "Crop not found: 42")
// rather than letting a raw exception/stack trace reach the client, and serializes
// successful results through the shared Jackson-3-backed McpJsonMapper.
final class McpToolSupport {

    private McpToolSupport() {
    }

    static McpSchema.CallToolResult execute(Logger logger, McpJsonMapper jsonMapper, Supplier<Object> action) {
        try {
            String json = jsonMapper.writeValueAsString(action.get());
            return McpSchema.CallToolResult.builder()
                    .addTextContent(json)
                    .build();
        } catch (CropNotFoundException | GoalNotFoundException | DomainValidationException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error handling MCP tool call", e);
            return error("An unexpected error occurred while processing this request.");
        }
    }

    private static McpSchema.CallToolResult error(String message) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(message)
                .isError(true)
                .build();
    }

    static Long requireLong(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (!(value instanceof Number number)) {
            throw new DomainValidationException(key + " is required and must be a number.");
        }
        return number.longValue();
    }

    static String optionalString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value instanceof String s ? s : null;
    }

    static Double optionalDouble(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value instanceof Number number ? number.doubleValue() : null;
    }

    static Integer optionalInteger(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    static Boolean optionalBoolean(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value instanceof Boolean b ? b : null;
    }

    static Instant optionalInstant(Map<String, Object> arguments, String key) {
        String value = optionalString(arguments, key);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new DomainValidationException(key + " must be an ISO-8601 timestamp, e.g. 2026-08-13T09:00:00Z.");
        }
    }

    static <E extends Enum<E>> E optionalEnum(Map<String, Object> arguments, String key, Class<E> enumType) {
        String value = optionalString(arguments, key);
        if (value == null) {
            return null;
        }
        return parseEnum(value, key, enumType);
    }

    static <E extends Enum<E>> E requireEnum(Map<String, Object> arguments, String key, Class<E> enumType) {
        String value = optionalString(arguments, key);
        if (value == null) {
            throw new DomainValidationException(key + " is required.");
        }
        return parseEnum(value, key, enumType);
    }

    private static <E extends Enum<E>> E parseEnum(String value, String key, Class<E> enumType) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException e) {
            String validValues = Arrays.stream(enumType.getEnumConstants())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new DomainValidationException(
                    "Invalid " + key + " '" + value + "'. Valid values: " + validValues + ".");
        }
    }
}
