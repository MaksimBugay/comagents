package com.comagents.agentproxy.agent_status;

import com.comagents.agentproxy.agent_setup.AgentSetupServices;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.response.AgentStatus;
import com.comagents.agentproxy.workspace.SessionFolders;
import jakarta.inject.Singleton;

import java.nio.file.Files;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Where an agent's session stands, so callers know when to fetch its response. */
@Singleton
public class AgentStatusService {

    private final AgentSetupServices agentSetupServices;

    private final SessionFolders sessionFolders;

    public AgentStatusService(AgentSetupServices agentSetupServices, SessionFolders sessionFolders) {
        this.agentSetupServices = agentSetupServices;
        this.sessionFolders = sessionFolders;
    }

    /** Empty when the session is unknown: never started, or already cleaned up. */
    public Optional<AgentStatus> findStatus(AiAgent agent, UUID sessionId) {
        return agentSetupServices.forAgent(agent)
                .flatMap(service -> service.status(sessionId))
                .map(status -> new AgentStatus(agent, sessionId, status.state(),
                        status.startedAt().toEpochMilli(),
                        status.finishedAt().map(Instant::toEpochMilli).orElse(null),
                        Files.isRegularFile(sessionFolders.agentResponseFile(sessionId, agent))));
    }
}
