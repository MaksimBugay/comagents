package com.comagents.commons.response;

import com.comagents.commons.communication.WithSequenceId;
import com.comagents.commons.domain.AiHarnessProvider;
import org.apache.commons.lang3.Validate;

import java.util.Objects;

/**
 * An agent executor (agent-proxy): the harness it runs agents with, its version, its device and how many sessions it can
 * still take. Equal by device id alone: two infos from the same executor are the same executor, whatever its load.
 *
 * @param harnessProvider  the harness the executor runs agents with
 * @param deviceId         the executor's device id
 * @param maxSlots         how many sessions the executor runs at once at most: its concurrent session limit
 * @param freeSlots        how many more sessions the executor can take: its concurrent session limit minus its
 *                         sessions running, waiting or being set up
 * @param archivedSessions the executor's archived sessions: they take no slot now, but each takes one again on resume
 * @param sequenceId       the start request's sequence id; a new random UUID when this answers no request ({@code GET /info})
 * @param createdAt        when the executor made it, in epoch millis; now when not given (0)
 */
public record AgentInfo(AiHarnessProvider harnessProvider, String version, String deviceId, int maxSlots, int freeSlots, int archivedSessions, String sequenceId, long createdAt) implements WithSequenceId {

    public AgentInfo {
        // the id of the ComagentsMessage that carries it
        Validate.notBlank(sequenceId, "sequenceId must not be blank");
        if (createdAt <= 0) {
            createdAt = System.currentTimeMillis();
        }
    }

    /** Created now. */
    public AgentInfo(AiHarnessProvider harnessProvider, String version, String deviceId, int maxSlots, int freeSlots,
                     int archivedSessions, String sequenceId) {
        this(harnessProvider, version, deviceId, maxSlots, freeSlots, archivedSessions, sequenceId,
                System.currentTimeMillis());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AgentInfo info && Objects.equals(deviceId, info.deviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(deviceId);
    }
}
