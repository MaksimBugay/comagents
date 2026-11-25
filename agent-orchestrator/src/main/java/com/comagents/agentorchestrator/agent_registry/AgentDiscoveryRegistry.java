package com.comagents.agentorchestrator.agent_registry;

import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.response.AgentInfoResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.Validate;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

/**
 * The agent executors that reported themselves lately, by device id: each is kept with its latest {@link AgentInfoResponse}
 * and forgotten {@code agent-orchestrator.discovery-ttl} (10 s by default, twice the executors' report interval) after
 * that info came in.
 */
@Singleton
public class AgentDiscoveryRegistry {

    // most free slots first; on a tie, fewest archived sessions, which each take a slot again on resume
    private static final Comparator<AgentInfoResponse> BEST_FIRST = Comparator.comparingInt(AgentInfoResponse::freeSlots).reversed()
            .thenComparingInt(AgentInfoResponse::archivedSessions);

    private final Cache<String, AgentInfoResponse> agentInfos;
    // updates one at a time, so an info that arrives meanwhile isn't overwritten by an older one; reads together,
    // never in the middle of an update
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public AgentDiscoveryRegistry(@Value("${agent-orchestrator.discovery-ttl:10s}") Duration ttl) {
        this.agentInfos = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .build();
    }

    /**
     * Adds the executor of {@code agentInfo}, or updates it when {@code agentInfo} is fresher than the one kept: an
     * older info, delivered late, neither replaces it nor keeps the executor longer.
     *
     * @throws NullPointerException     when the device id is null
     * @throws IllegalArgumentException when the device id is blank
     */
    public void update(AgentInfoResponse agentInfo) {
        Validate.notBlank(agentInfo.deviceId(), "deviceId must not be blank");
        lock.writeLock().lock();
        try {
            AgentInfoResponse kept = agentInfos.getIfPresent(agentInfo.deviceId());
            if (kept == null || agentInfo.createdAt() > kept.createdAt()) {
                agentInfos.put(agentInfo.deviceId(), agentInfo);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Forgets the executor on {@code deviceId} until its next discovery report, e.g. after it refused a session for
     * lack of slots.
     */
    public void remove(String deviceId) {
        lock.writeLock().lock();
        try {
            agentInfos.invalidate(deviceId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** All executors known now, also those without a free slot, best first. */
    public List<AgentInfoResponse> getAll() {
        return find(_ -> true);
    }

    /**
     * The executors that can take a session now, best first: most free slots, then fewest archived sessions.
     *
     * @param oneSessionPerExecutor only executors with nothing on them: no running, waiting or archived sessions, and
     *                              no finished ones whose data is still on disk
     *                              ({@code maxSlots - freeSlots + archivedSessions + finishedSessions == 0}), so a
     *                              session never shares an executor with another, nor with what another left
     */
    public List<AgentInfoResponse> getAllAvailable(boolean oneSessionPerExecutor) {
        Predicate<AgentInfoResponse> available = info -> info.freeSlots() > 0;
        if (oneSessionPerExecutor) {
            available = available.and(AgentDiscoveryRegistry::isIdle);
        }
        return find(available);
    }

    /**
     * The best executor of {@code harnessProvider} that can take a session now ({@link #getAllAvailable}).
     */
    public Optional<AgentInfoResponse> getBestAvailable(AiHarnessProvider harnessProvider,
                                                        boolean oneSessionPerExecutor) {
        return getAllAvailable(oneSessionPerExecutor).stream()
                .filter(info -> info.harnessProvider() == harnessProvider)
                .findFirst();
    }

    private List<AgentInfoResponse> find(Predicate<AgentInfoResponse> filter) {
        lock.readLock().lock();
        try {
            // the map view leaves out expired entries
            return agentInfos.asMap().values().stream()
                    .filter(filter)
                    .sorted(BEST_FIRST)
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    private static boolean isIdle(AgentInfoResponse info) {
        return info.maxSlots() - info.freeSlots() + info.archivedSessions() + info.finishedSessions() == 0;
    }
}
