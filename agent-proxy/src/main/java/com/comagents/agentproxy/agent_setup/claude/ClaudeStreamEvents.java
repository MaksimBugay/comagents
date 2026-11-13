package com.comagents.agentproxy.agent_setup.claude;

import io.micronaut.json.tree.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads Claude Code's {@code --output-format stream-json} events. Summaries are for logs: they name the tool
 * and its main argument but never include tool results, which can hold whole files or secrets the agent read.
 */
final class ClaudeStreamEvents {

    static final int MAX_SUMMARY_LENGTH = 300;

    // the input field that best identifies what a tool call works on, in order of preference
    private static final List<String> TOOL_TARGET_FIELDS = List.of("file_path", "path", "command", "pattern", "url", "query", "description");

    private static final String THINKING = "thinking";
    private static final String THINKING_TOKENS = "thinking_tokens";

    private ClaudeStreamEvents() {
    }

    /** One-line summary of an event, or empty for events not worth logging (tool results). */
    static Optional<String> summarize(JsonNode event) {
        String type = text(event, "type");
        return switch (type == null ? "" : type) {
            case "system" -> Optional.of(summarizeSystem(event));
            case "assistant" -> summarizeAssistant(event);
            case "user" -> Optional.empty();
            case "result" -> Optional.of(summarizeResult(event));
            default -> Optional.of("event: " + (type == null ? "unknown" : type));
        };
    }

    /** Thinking and thinking-token progress: detail for verbose output only, and by far the most frequent events. */
    static boolean isVerbose(JsonNode event) {
        String type = text(event, "type");
        if ("system".equals(type)) {
            return THINKING_TOKENS.equals(text(event, "subtype"));
        }
        if (!"assistant".equals(type)) {
            return false;
        }
        JsonNode content = content(event);
        if (content == null || content.size() == 0) {
            return false;
        }
        for (JsonNode block : content.values()) {
            if (!THINKING.equals(text(block, "type"))) {
                return false;
            }
        }
        return true;
    }

    /** Whether a {@code user} event holds a tool result whose text starts with {@code prefix}. */
    static boolean hasToolResultStartingWith(JsonNode event, String prefix) {
        if (!"user".equals(text(event, "type"))) {
            return false;
        }
        JsonNode content = content(event);
        if (content == null) {
            return false;
        }
        for (JsonNode block : content.values()) {
            if ("tool_result".equals(text(block, "type")) && toolResultText(block.get("content")).startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // a tool result's content: a string, or text blocks
    private static String toolResultText(JsonNode content) {
        if (content == null) {
            return "";
        }
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode block : content.values()) {
                if ("text".equals(text(block, "type"))) {
                    text.append(text(block, "text"));
                }
            }
            return text.toString();
        }
        return content.isString() ? content.getStringValue() : "";
    }

    static boolean isResult(JsonNode event) {
        return "result".equals(text(event, "type"));
    }

    /** The final answer of a {@code result} event. */
    static Optional<String> resultText(JsonNode event) {
        return Optional.ofNullable(text(event, "result"));
    }

    static boolean isSuccessfulResult(JsonNode event) {
        JsonNode isError = event.get("is_error");
        return "success".equals(text(event, "subtype")) && (isError == null || !isError.getBooleanValue());
    }

    static String truncate(String text) {
        String singleLine = text.replaceAll("\\s+", " ").strip();
        return singleLine.length() <= MAX_SUMMARY_LENGTH ? singleLine : singleLine.substring(0, MAX_SUMMARY_LENGTH) + "…";
    }

    private static String summarizeSystem(JsonNode event) {
        String subtype = text(event, "subtype");
        if (!"init".equals(subtype)) {
            return "system: " + subtype;
        }
        JsonNode tools = event.get("tools");
        return "init: model=" + text(event, "model")
                + ", tools=" + (tools == null ? 0 : tools.size())
                + ", mcp=" + mcpServerNames(event.get("mcp_servers"));
    }

    // thinking is summarised only when the event holds nothing else (see isVerbose), so it never mixes into a
    // summary that is shown without verbose output
    private static Optional<String> summarizeAssistant(JsonNode event) {
        JsonNode content = content(event);
        if (content == null) {
            return Optional.empty();
        }
        List<String> parts = new ArrayList<>();
        List<String> thinking = new ArrayList<>();
        for (JsonNode block : content.values()) {
            String blockType = text(block, "type");
            if ("text".equals(blockType)) {
                parts.add("text: " + truncate(String.valueOf(text(block, "text"))));
            } else if ("tool_use".equals(blockType)) {
                parts.add("tool: " + text(block, "name") + toolTarget(block.get("input")));
            } else if (THINKING.equals(blockType)) {
                thinking.add("thinking: " + truncate(String.valueOf(text(block, THINKING))));
            }
        }
        List<String> shown = parts.isEmpty() ? thinking : parts;
        return shown.isEmpty() ? Optional.empty() : Optional.of(truncate(String.join(" | ", shown)));
    }

    // the content blocks of an assistant message
    private static JsonNode content(JsonNode event) {
        JsonNode message = event.get("message");
        JsonNode content = message == null ? null : message.get("content");
        return content != null && content.isArray() ? content : null;
    }

    private static String summarizeResult(JsonNode event) {
        StringBuilder summary = new StringBuilder("done: ").append(text(event, "subtype"));
        JsonNode turns = event.get("num_turns");
        if (turns != null && turns.isNumber()) {
            summary.append(", ").append(turns.getIntValue()).append(" turns");
        }
        JsonNode cost = event.get("total_cost_usd");
        if (cost != null && cost.isNumber()) {
            summary.append(String.format(Locale.ROOT, ", $%.4f", cost.getDoubleValue()));
        }
        JsonNode duration = event.get("duration_ms");
        if (duration != null && duration.isNumber()) {
            summary.append(String.format(Locale.ROOT, ", %.1fs", duration.getLongValue() / 1000.0));
        }
        return summary.toString();
    }

    private static String toolTarget(JsonNode input) {
        if (input == null || !input.isObject()) {
            return "";
        }
        for (String field : TOOL_TARGET_FIELDS) {
            String value = text(input, field);
            if (value != null) {
                return " " + truncate(value);
            }
        }
        return "";
    }

    private static List<String> mcpServerNames(JsonNode servers) {
        List<String> names = new ArrayList<>();
        if (servers != null && servers.isArray()) {
            for (JsonNode server : servers.values()) {
                names.add(server.isObject() ? String.valueOf(text(server, "name")) : server.coerceStringValue());
            }
        }
        return names;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null || !node.isObject() ? null : node.get(field);
        return value == null || value.isNull() ? null : value.coerceStringValue();
    }
}
