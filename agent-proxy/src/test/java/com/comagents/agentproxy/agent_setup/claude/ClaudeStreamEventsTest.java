package com.comagents.agentproxy.agent_setup.claude;

import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaudeStreamEventsTest {

    private static final ObjectMapper JSON = ObjectMapper.getDefault();

    @Test
    void summarizesInit() throws IOException {
        JsonNode event = parse("""
                {"type":"system","subtype":"init","model":"claude-x","tools":["Read","Bash"],"mcp_servers":[{"name":"agentproxy","status":"connected"}]}""");

        assertEquals(Optional.of("init: model=claude-x, tools=2, mcp=[agentproxy]"), ClaudeStreamEvents.summarize(event));
    }

    @Test
    void summarizesToolCallsAndTextButNotToolResults() throws IOException {
        JsonNode toolCall = parse("""
                {"type":"assistant","message":{"content":[{"type":"text","text":"Checking\\nthe service"},{"type":"tool_use","name":"Bash","input":{"command":"./mvnw -B -q test","description":"run tests"}}]}}""");
        JsonNode toolResult = parse("""
                {"type":"user","message":{"content":[{"type":"tool_result","content":"SECRET=abc"}]}}""");

        assertEquals(Optional.of("text: Checking the service | tool: Bash ./mvnw -B -q test"), ClaudeStreamEvents.summarize(toolCall));
        assertEquals(Optional.empty(), ClaudeStreamEvents.summarize(toolResult));
    }

    @Test
    void summarizesAndReadsResult() throws IOException {
        JsonNode event = parse("""
                {"type":"result","subtype":"success","is_error":false,"num_turns":42,"total_cost_usd":0.87,"duration_ms":372000,"result":"# Report"}""");

        assertEquals(Optional.of("done: success, 42 turns, $0.8700, 372.0s"), ClaudeStreamEvents.summarize(event));
        assertTrue(ClaudeStreamEvents.isResult(event));
        assertTrue(ClaudeStreamEvents.isSuccessfulResult(event));
        assertEquals(Optional.of("# Report"), ClaudeStreamEvents.resultText(event));
    }

    @Test
    void errorResultIsNotSuccessful() throws IOException {
        assertFalse(ClaudeStreamEvents.isSuccessfulResult(parse("""
                {"type":"result","subtype":"error_max_turns","is_error":true}""")));
    }

    @Test
    void truncatesLongText() {
        String summary = ClaudeStreamEvents.truncate("x".repeat(1000));

        assertEquals(ClaudeStreamEvents.MAX_SUMMARY_LENGTH + 1, summary.length());
        assertTrue(summary.endsWith("…"));
    }

    private static JsonNode parse(String json) throws IOException {
        return JSON.readValue(json, JsonNode.class);
    }
}
