package com.comagents.agentproxy.mcp;

import com.comagents.agentproxy.agent_call.AgentCallResponses;
import com.comagents.agentproxy.agent_call.AgentCallToolDefinition;
import com.comagents.agentproxy.agent_call.AgentOrchestratorClient;
import com.comagents.agentproxy.agent_setup.claude.AgentProxyMcpServer;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSession;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.commons.domain.AgentSessionAttributes;
import com.comagents.commons.domain.AgentSessionLoggingLevel;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.request.StartAgentSessionExternalRequest;
import com.comagents.commons.response.AgentSessionResponse;
import com.comagents.commons.serialization.JsonUtility;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator.ValidationResponse;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The agent tools ({@code mcp__agentproxy__<agent name>}, see {@link AgentCallTools}): a Claude Code run calls another
 * agent and gets its response as the tool's result. The called agent's session is started through the orchestrator,
 * with this executor as the device that waits for its response, and the caller's call chain
 * ({@code agentCallSequence}), which the orchestrator grows by the called agent; a call that would make a loop is
 * refused. The call waits up to {@code agent-question.answer-timeout}, with the session {@code WAITING}, as
 * {@link AskExpertTool} does. Without a response by then, the call is deferred: the agent is told to end its turn, the
 * session is archived when it has, and the response resumes it whenever it comes. A response is checked against the
 * called agent's response schema. Refusals are error results the agent reads, not exceptions.
 */
@Singleton
public class AgentCallTool {

    private static final Logger LOG = LoggerFactory.getLogger(AgentCallTool.class);

    private final ClaudeAgentSessionRegistry sessions;
    private final AgentCallResponses responses;
    private final AgentOrchestratorClient orchestrator;
    private final AgentProxyConf conf;
    private final JsonSchemaValidator schemaValidator;
    private final Duration answerTimeout;

    public AgentCallTool(ClaudeAgentSessionRegistry sessions, AgentCallResponses responses,
                         AgentOrchestratorClient orchestrator, AgentProxyConf conf, JsonSchemaValidator schemaValidator,
                         @Value("${agent-question.answer-timeout}") Duration answerTimeout) {
        this.sessions = sessions;
        this.responses = responses;
        this.orchestrator = orchestrator;
        this.conf = conf;
        this.schemaValidator = schemaValidator;
        this.answerTimeout = answerTimeout;
    }

    // 429: no executor of the agent has a free slot now, a state that passes; anything else is a refusal
    static String notStarted(AiAgent agent, HttpClientResponseException e) {
        if (e.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
            return "The " + agent.getAgentName() + " agent is busy: all its sessions are taken (" + e.getMessage()
                    + "). It was not called. Call it again later, or continue without it and say in your final answer "
                    + "what is missing because of that.";
        }
        return "The orchestrator did not start the " + agent.getAgentName() + " agent (" + e.getStatus().getCode()
                + "): " + e.getMessage();
    }

    // must never read like a response: the agent would act on it
    static String endTurn(AiAgent agent) {
        return AgentProxyMcpServer.NO_ANSWER_YET + " The " + agent.getAgentName() + " agent has not responded yet. "
                + "This is not its response and says nothing about it: do not act on it. The call stays open. End your "
                + "turn now: do no more work and give no final answer or report. You will be resumed with the agent's "
                + "response in a new message.";
    }

    /**
     * Calls the agent of {@code definition} for the run that sent the request.
     *
     * @param arguments the tool's input: the called agent's agent context ({@code {"items": [...]}})
     */
    public CallToolResult call(AgentCallToolDefinition definition, Map<String, Object> arguments,
                               McpTransportContext context) {
        AiAgent agent = definition.agent();
        // the deadline counts from the call: the start through the orchestrator takes from the same time budget
        Instant deadline = Instant.now().plus(answerTimeout);
        Optional<ClaudeAgentSession> caller = McpCaller.find(sessions, context);
        if (caller.isEmpty()) {
            return error("Unknown session or wrong session token");
        }
        ClaudeAgentSession session = caller.get();
        List<String> callSequence;
        AgentContext agentContext;
        try {
            callSequence = callSequence(session, agent);
            agentContext = JsonUtility.convertToObjectFromMap(arguments, AgentContext.class);
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
        try (ClaudeAgentSessionRegistry.Waiting _ = sessions.markWaiting(session.getSessionId())) {
            UUID calledSessionId;
            CompletableFuture<AgentSessionResponse> response;
            // a response that comes before the call waits for it is kept until then
            responses.startingCall();
            try {
                calledSessionId = start(session, agent, agentContext, callSequence);
                response = responses.expect(calledSessionId);
            } finally {
                responses.startFinished();
            }
            return await(session, definition, calledSessionId, response, deadline);
        } catch (HttpClientResponseException e) {
            return error(notStarted(agent, e));
        } catch (HttpClientException e) {
            return error("The orchestrator can't be reached to start the " + agent.getAgentName() + " agent: "
                    + e.getMessage());
        } catch (IllegalStateException e) {
            return error(e.getMessage());
        }
    }

    /**
     * The call chain sent with a call of {@code called}: the caller's own, which the orchestrator ended with the caller
     * when it started it. The orchestrator appends the called agent.
     *
     * @throws IllegalArgumentException when the called agent is the caller or in its chain already: calls must not loop
     */
    static List<String> callSequence(ClaudeAgentSession caller, AiAgent called) {
        AgentSessionAttributes attributes = caller.getOptions().attributes();
        List<String> sequence = attributes == null ? List.of() : attributes.agentCallSequence();
        if (called == caller.getAgent() || sequence.contains(called.getAgentName())) {
            String chain = sequence.isEmpty() ? caller.getAgent().getAgentName() : String.join(" -> ", sequence);
            throw new IllegalArgumentException("Calling the " + called.getAgentName() + " agent would make a loop: it "
                    + "is already in the call chain " + chain);
        }
        return sequence;
    }

    private UUID start(ClaudeAgentSession caller, AiAgent agent, AgentContext agentContext, List<String> callSequence) {
        AgentSessionAttributes attributes = caller.getOptions().attributes();
        AgentSessionLoggingLevel loggingLevel = attributes == null ? AgentSessionLoggingLevel.INFO : attributes.loggingLevel();
        UUID calledSessionId = orchestrator.start(new StartAgentSessionExternalRequest(agent, agentContext,
                callSequence, caller.getOptions().actorAccountId(), conf.getDeviceId(), loggingLevel)).sessionId();
        LOG.info("[{} {}] called {} as session {} (call chain {})", caller.getAgent().getAgentName(),
                caller.getSessionId(), agent.getAgentName(), calledSessionId, callSequence);
        return calledSessionId;
    }

    private CallToolResult await(ClaudeAgentSession caller, AgentCallToolDefinition definition, UUID calledSessionId,
                                 CompletableFuture<AgentSessionResponse> response, Instant deadline) {
        AiAgent agent = definition.agent();
        try {
            long waitMillis = Math.max(0, Duration.between(Instant.now(), deadline).toMillis());
            AgentSessionResponse responded = response.get(waitMillis, TimeUnit.MILLISECONDS);
            LOG.info("[{} {}] {} session {} responded", caller.getAgent().getAgentName(), caller.getSessionId(),
                    agent.getAgentName(), calledSessionId);
            return result(definition, responded);
        } catch (TimeoutException e) {
            return defer(caller, definition, calledSessionId, response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return defer(caller, definition, calledSessionId, response);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Waiting for the " + agent.getAgentName() + " agent's response failed", e.getCause());
        } finally {
            responses.forget(calledSessionId, response);
        }
    }

    // deferred first: from then on a response that misses the call resumes the session instead of getting lost
    private CallToolResult defer(ClaudeAgentSession caller, AgentCallToolDefinition definition, UUID calledSessionId,
                                 CompletableFuture<AgentSessionResponse> response) {
        AiAgent agent = definition.agent();
        UUID sessionId = caller.getSessionId();
        sessions.deferAgentCall(sessionId, agent, calledSessionId);
        if (!responses.forget(calledSessionId, response)) {
            sessions.cancelDeferredAgentCall(sessionId);
            LOG.info("[{} {}] {} session {} responded just in time", caller.getAgent().getAgentName(), sessionId,
                    agent.getAgentName(), calledSessionId);
            return result(definition, response.join());
        }
        LOG.info("[{} {}] no response of {} session {} within {}, the session is archived until it comes",
                caller.getAgent().getAgentName(), sessionId, agent.getAgentName(), calledSessionId,
                AskExpertTool.describe(answerTimeout));
        return CallToolResult.builder().addTextContent(endTurn(agent)).isError(false).build();
    }

    // the called agent's response, as JSON, when it matches the agent's response schema
    private CallToolResult result(AgentCallToolDefinition definition, AgentSessionResponse response) {
        String text = AgentCallResponses.toText(response);
        ValidationResponse validation = schemaValidator.validate(JsonUtility.convertToMap(definition.responseSchema()),
                JsonUtility.convertToMap(response.agentContext()));
        if (!validation.valid()) {
            return error("The response of the " + definition.agent().getAgentName() + " agent does not match its "
                    + "response schema (" + validation.errorMessage() + "): " + text);
        }
        return CallToolResult.builder().addTextContent(text).isError(false).build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder().addTextContent(message).isError(true).build();
    }
}
