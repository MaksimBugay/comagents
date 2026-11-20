package com.comagents.commons.response;

import com.comagents.commons.communication.WithSequenceId;
import org.apache.commons.lang3.Validate;

/**
 * An agent-proxy (agent executor) and the agent it runs.
 *
 * @param deviceId         the executor's device id
 * @param freeSlots        how many more sessions the executor can take: its concurrent session limit minus its
 *                         sessions running, waiting or being set up
 * @param archivedSessions the executor's archived sessions: they take no slot now, but each takes one again on resume
 * @param sequenceId       the start request's sequence id; a new random UUID when this answers no request ({@code GET /info})
 */
public record AgentInfo(String name, String version, String deviceId, int freeSlots, int archivedSessions, String sequenceId) implements WithSequenceId {

    public AgentInfo {
        // the id of the ComagentsMessage that carries it
        Validate.notBlank(sequenceId, "sequenceId must not be blank");
    }
}
