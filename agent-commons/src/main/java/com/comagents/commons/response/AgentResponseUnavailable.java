package com.comagents.commons.response;

import com.comagents.commons.domain.AgentSessionState;
import org.jspecify.annotations.Nullable;

/**
 * Body of the 404 when an agent has no response for a session.
 *
 * @param state where the session stands: {@code RUNNING} or {@code WAITING} means try again later, any other
 *              state means no response will come; null when the session is unknown (never started, or already
 *              cleaned up)
 */
public record AgentResponseUnavailable(String message, @Nullable AgentSessionState state) {
}
