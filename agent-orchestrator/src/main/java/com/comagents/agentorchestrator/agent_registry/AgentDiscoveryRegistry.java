package com.comagents.agentorchestrator.agent_registry;

import com.comagents.commons.response.AgentInfo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.CollectionUtils;
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
 * The agent executors that reported themselves lately, by device id: each is kept with its latest {@link AgentInfo}
 * and forgotten {@code agent-orchestrator.discovery-ttl} (10 s by default, twice the executors' report interval) after
 * that info came in.
 */
@Singleton
public class AgentDiscoveryRegistry {

    // most free slots first; on a tie, fewest archived sessions, which each take a slot again on resume
    private static final Comparator<AgentInfo> BEST_FIRST = Comparator.comparingInt(AgentInfo::freeSlots).reversed()
            .thenComparingInt(AgentInfo::archivedSessions);

    private final Cache<String, AgentInfo> agentInfos;
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
    public void update(AgentInfo agentInfo) {
        Validate.notBlank(agentInfo.deviceId(), "deviceId must not be blank");
        lock.writeLock().lock();
        try {
            AgentInfo kept = agentInfos.getIfPresent(agentInfo.deviceId());
            if (kept == null || agentInfo.createdAt() > kept.createdAt()) {
                agentInfos.put(agentInfo.deviceId(), agentInfo);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** All executors known now, also those without a free slot, best first. */
    public List<AgentInfo> getAll() {
        return find(_ -> true);
    }

    /**
     * The executors that can take a session now, best first: most free slots, then fewest archived sessions.
     *
     * @param oneSessionPerExecutor only executors with nothing on them, neither running, waiting nor archived sessions
     *                              ({@code maxSlots - freeSlots + archivedSessions == 0}), so a session never shares an
     *                              executor with another
     */
    public List<AgentInfo> getAllAvailable(boolean oneSessionPerExecutor) {
        Predicate<AgentInfo> available = info -> info.freeSlots() > 0;
        if (oneSessionPerExecutor) {
            available = available.and(AgentDiscoveryRegistry::isIdle);
        }
        return find(available);
    }

    public Optional<AgentInfo> getBestAvailable(boolean oneSessionPerExecutor) {
        List<AgentInfo> all = getAllAvailable(oneSessionPerExecutor);
        if (CollectionUtils.isEmpty(all)) {
            return Optional.empty();
        }
        return Optional.of(all.getFirst());
    }

    private List<AgentInfo> find(Predicate<AgentInfo> filter) {
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

    private static boolean isIdle(AgentInfo info) {
        return info.maxSlots() - info.freeSlots() + info.archivedSessions() == 0;
    }
}
