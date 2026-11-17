package com.comagents.agentproxy.conf;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which agent-proxy this is, in this order: {@code agent-proxy.device-id} when set; the container id under Docker
 * (Swarm too); the pod UID under Kubernetes; otherwise the host name. Both ids are read from the container's mount
 * table, the only place cgroup v2 still shows them: Docker mounts {@code /etc/hostname} from
 * {@code .../docker/containers/<container id>/hostname}, the kubelet {@code /etc/hosts} from
 * {@code .../pods/<pod UID>/etc-hosts}.
 */
@Singleton
public class DeviceIdExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceIdExtractor.class);

    private static final Path MOUNT_INFO = Path.of("/proc/self/mountinfo");
    private static final Pattern DOCKER_CONTAINER_ID = Pattern.compile("/docker/containers/([0-9a-f]{64})/");
    private static final Pattern KUBERNETES_POD_UID = Pattern.compile(
            "/pods/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/");
    private static final String NO_HOSTNAME = "local";

    private final String configuredDeviceId;
    private final String hostname;

    public DeviceIdExtractor(@Value("${agent-proxy.device-id:}") String configuredDeviceId,
                             @Value("${HOSTNAME:" + NO_HOSTNAME + "}") String hostname) {
        this.configuredDeviceId = configuredDeviceId;
        this.hostname = hostname;
    }

    public String extract() {
        return extract(configuredDeviceId, MOUNT_INFO, hostname);
    }

    static String extract(@Nullable String configuredDeviceId, Path mountInfo, String hostname) {
        if (StringUtils.isNotBlank(configuredDeviceId)) {
            return configuredDeviceId.strip();
        }
        String mounts = readMountInfo(mountInfo);
        return find(DOCKER_CONTAINER_ID, mounts)
                .or(() -> find(KUBERNETES_POD_UID, mounts))
                .orElse(hostname);
    }

    private static Optional<String> find(Pattern id, String mounts) {
        Matcher matcher = id.matcher(mounts);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    // empty outside Linux (local runs on macOS)
    private static String readMountInfo(Path mountInfo) {
        if (!Files.isReadable(mountInfo)) {
            return "";
        }
        try {
            return Files.readString(mountInfo);
        } catch (IOException e) {
            LOG.warn("Can't read {}: device id falls back to the host name", mountInfo, e);
            return "";
        }
    }
}
