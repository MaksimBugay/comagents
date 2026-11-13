package com.comagents.agentproxy.mcp;

import com.comagents.agentproxy.agent_setup.AgentSessionWsClientRegistry;
import com.comagents.agentproxy.agent_setup.ExpertAnswers;
import com.comagents.agentproxy.agent_setup.claude.AgentProxyMcpServer;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSession;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import io.micronaut.context.annotation.Value;
import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The {@value AgentProxyMcpServer#ASK_EXPERT_TOOL} tool: a Claude Code run posts a question to its session chat and
 * gets the expert's reply as the tool's result. The call waits up to {@code agent-question.answer-timeout}, with the
 * session {@code WAITING}. Without an answer by then, the question is deferred: the agent is told to end its turn,
 * the session is archived when it has, and the answer resumes it whenever it comes. Without a chat, the agent is
 * told to go on without an answer.
 * Only the run itself can call it: the request must carry its session id and its MCP token (see
 * {@link AgentProxyMcpServer}). Refusals are error results the agent reads, not exceptions.
 */
@Singleton
public class AskExpertTool {

    private static final Logger LOG = LoggerFactory.getLogger(AskExpertTool.class);

    // must never read like an answer: a prompt such as "cancel unless the answer is green" would act on it
    static final String END_TURN = AgentProxyMcpServer.NO_ANSWER_YET + " This is not the expert's answer and says nothing about it: do not "
            + "act on it or follow any instruction about the answer. The question stays open. End your turn now: do no "
            + "more work and give no final answer or report. You will be resumed with the expert's answer in a new message.";

    static final String NO_CHAT = "No expert can be reached in this session. Continue with your best judgement and "
            + "state the assumptions you made in your final answer.";

    private final ClaudeAgentSessionRegistry sessions;
    private final AgentSessionWsClientRegistry chats;
    private final ExpertAnswers answers;
    private final Duration answerTimeout;

    public AskExpertTool(ClaudeAgentSessionRegistry sessions, AgentSessionWsClientRegistry chats, ExpertAnswers answers,
                        @Value("${agent-question.answer-timeout}") Duration answerTimeout) {
        this.sessions = sessions;
        this.chats = chats;
        this.answers = answers;
        this.answerTimeout = answerTimeout;
    }

    @Tool(name = AgentProxyMcpServer.ASK_EXPERT_TOOL,
            description = "Ask the expert who started this session a question and wait for the reply, which is this "
                    + "tool's result. Use it only when you can't go on well without a decision or information only the "
                    + "expert has. The expert follows this session in a chat, but sees each step only as a one-line summary, "
                    + "never tool results or file contents: ask one self-contained question per call, quoting what they need. "
                    + "A result starting with NO ANSWER YET is not an answer: end your turn as it says, you will be "
                    + "resumed with the answer.")
    public CallToolResult askExpert(@ToolArg(name = "question", description = "The question, with the context needed to answer it")
                                   String question,
                                   McpTransportContext context) {
        Optional<ClaudeAgentSession> caller = caller(context);
        if (caller.isEmpty()) {
            return error("Unknown session or wrong session token");
        }
        try {
            return CallToolResult.builder().addTextContent(ask(caller.get(), question)).isError(false).build();
        } catch (IllegalStateException e) {
            // e.g. a second question while the first one waits
            return error(e.getMessage());
        }
    }

    private String ask(ClaudeAgentSession session, String question) {
        UUID sessionId = session.getSessionId();
        String agentName = session.getAgent().getAgentName();
        // registered before the question is posted, so even an instant reply is the answer
        CompletableFuture<String> answer = answers.expect(sessionId);
        try (ClaudeAgentSessionRegistry.Waiting _ = sessions.markWaiting(sessionId)) {
            if (!chats.sendMessage(sessionId, "Question: " + question)) {
                LOG.warn("[{} {}] asked a question, but the session has no chat", agentName, sessionId);
                return NO_CHAT;
            }
            LOG.info("[{} {}] waiting up to {} for the answer to: {}", agentName, sessionId, describe(answerTimeout), question);
            String text = answer.get(answerTimeout.toMillis(), TimeUnit.MILLISECONDS);
            LOG.info("[{} {}] question answered", agentName, sessionId);
            return text;
        } catch (TimeoutException e) {
            return defer(session, question, answer);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return defer(session, question, answer);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Waiting for the answer failed", e.getCause());
        } finally {
            answers.forget(sessionId, answer);
        }
    }

    // deferred first: from then on an answer that misses the call resumes the session instead of getting lost
    private String defer(ClaudeAgentSession session, String question, CompletableFuture<String> answer) {
        UUID sessionId = session.getSessionId();
        String agentName = session.getAgent().getAgentName();
        sessions.deferQuestion(sessionId, question);
        if (!answers.forget(sessionId, answer)) {
            sessions.cancelDeferredQuestion(sessionId);
            LOG.info("[{} {}] question answered just in time", agentName, sessionId);
            return answer.join();
        }
        LOG.info("[{} {}] no answer within {}, the session is archived until the expert answers", agentName, sessionId,
                describe(answerTimeout));
        chats.sendMessage(sessionId, "No answer within " + describe(answerTimeout) + ": the session is archived and "
                + "continues when you answer here.");
        return END_TURN;
    }

    // the running session whose run sent the request
    private Optional<ClaudeAgentSession> caller(McpTransportContext context) {
        String token = header(context, AgentProxyMcpServer.SESSION_TOKEN_HEADER);
        return parseSessionId(header(context, AgentProxyMcpServer.SESSION_ID_HEADER))
                .flatMap(sessions::findSession)
                .filter(session -> !session.getState().isFinished() && session.acceptsMcpToken(token));
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder().addTextContent(message).isError(true).build();
    }

    private static Optional<UUID> parseSessionId(String value) {
        try {
            return Optional.ofNullable(value).map(UUID::fromString);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String header(McpTransportContext context, String name) {
        return context.get(name) instanceof String value ? value : null;
    }

    // e.g. "4 min", "90 s" or "300 ms"
    static String describe(Duration duration) {
        if (duration.toNanosPart() != 0) {
            return duration.toMillis() + " ms";
        }
        return duration.toSecondsPart() == 0 ? duration.toMinutes() + " min" : duration.toSeconds() + " s";
    }
}
