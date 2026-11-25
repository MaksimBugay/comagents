package com.comagents.agentproxy.mcp;

import com.comagents.agentproxy.agent_call.AgentCallToolDefinition;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionArchive;
import com.comagents.agentproxy.workspace.SessionFolders;
import com.comagents.commons.serialization.JsonUtility;
import com.comagents.commons.serialization.SerializationException;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The agent tools of agent-proxy's MCP server, added while it runs: a session whose agent allows
 * {@code mcp__agentproxy__<agent name>} gets that agent as a tool ({@link AgentCallTool}), described by the called
 * agent's {@code exposeAsMcp}. The tools of a session are kept in its agent folder ({@value #FILE_NAME}), so after a
 * restart those of the archived sessions, which can be resumed, are added again. One tool per agent: a session
 * that adds it with the same definition keeps it, one with a changed definition (the called agent's
 * {@code agent.json} changed) reloads it.
 */
@Singleton
public class AgentCallTools implements ApplicationEventListener<StartupEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(AgentCallTools.class);

    static final String FILE_NAME = "agent-tools.json";

    private final McpStatelessSyncServer server;
    private final AgentCallTool agentCallTool;
    private final ClaudeAgentSessionRegistry sessions;
    private final SessionFolders sessionFolders;
    // guarded by this
    private final Map<String, AgentCallToolDefinition> added = new HashMap<>();

    public AgentCallTools(McpStatelessSyncServer server, AgentCallTool agentCallTool,
                          ClaudeAgentSessionRegistry sessions, SessionFolders sessionFolders) {
        this.server = server;
        this.agentCallTool = agentCallTool;
        this.sessions = sessions;
        this.sessionFolders = sessionFolders;
    }

    /** Adds a session's agent tools and keeps them in its {@code agentFolder}; nothing for none. */
    public void add(Path agentFolder, List<AgentCallToolDefinition> definitions) {
        if (definitions.isEmpty()) {
            return;
        }
        try {
            Files.write(agentFolder.resolve(FILE_NAME), JsonUtility.toJsonAsBytes(definitions));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save the agent tools in " + agentFolder, e);
        }
        definitions.forEach(this::add);
    }

    @Override
    public void onApplicationEvent(@NonNull StartupEvent event) {
        for (ClaudeSessionArchive archive : sessions.findArchives()) {
            Path file = sessionFolders.agentFolder(archive.sessionId(), archive.agent()).resolve(FILE_NAME);
            if (Files.isRegularFile(file)) {
                try {
                    read(file).forEach(this::add);
                } catch (RuntimeException e) {
                    LOG.warn("Skipping the agent tools of archived session {}: {}", archive.sessionId(), e.getMessage());
                }
            }
        }
    }

    private synchronized void add(AgentCallToolDefinition definition) {
        String name = definition.agent().getAgentName();
        AgentCallToolDefinition previous = added.put(name, definition);
        if (definition.equals(previous)) {
            return;
        }
        if (previous != null) {
            // its agent.json changed since: the tool is reloaded
            server.removeTool(name);
        }
        McpSchema.Tool tool = McpSchema.Tool.builder(name, JsonUtility.convertToMap(definition.requestSchema()))
                .description(definition.toolDescription())
                .annotations(new McpSchema.ToolAnnotations(null, definition.readOnly(), null, null, null, null))
                .build();
        server.addTool(new SyncToolSpecification(tool,
                (context, request) -> agentCallTool.call(definition, request.arguments(), context)));
        LOG.info("Agent tool {} {} the MCP server", name, previous == null ? "added to" : "reloaded in");
    }

    private static List<AgentCallToolDefinition> read(Path file) {
        try {
            return JsonUtility.fromJsonToList(Files.readString(file), AgentCallToolDefinition.class);
        } catch (IOException | SerializationException e) {
            throw new IllegalStateException("Unreadable " + file, e);
        }
    }
}
