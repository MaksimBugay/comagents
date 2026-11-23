package com.comagents.commons.communication;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which device this process is, in this order: the configured device id when set; the container id under Docker
 * (Swarm too); the pod UID under Kubernetes; otherwise the host name. Both ids are read from the container's mount
 * table, the only place cgroup v2 still shows them: Docker mounts {@code /etc/hostname} from
 * {@code .../docker/containers/<container id>/hostname}, the kubelet {@code /etc/hosts} from
 * {@code .../pods/<pod UID>/etc-hosts}.
 * <p>
 * A container id is one container in the whole Swarm. A pod UID is shared by the pod's containers, and a host name
 * can be shared too (a Swarm {@code hostname} template, the same pod name in two namespaces), so both get a random
 * suffix: each service instance is a device of its own. A configured device id is the operator's and is kept as is.
 */
public class DeviceIdExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceIdExtractor.class);

    private static final Path MOUNT_INFO = Path.of("/proc/self/mountinfo");
    private static final Pattern DOCKER_CONTAINER_ID = Pattern.compile("/docker/containers/([0-9a-f]{64})/");
    private static final Pattern KUBERNETES_POD_UID = Pattern.compile(
            "/pods/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/");
    private static final int SUFFIX_LENGTH = 12;

    private final @Nullable String configuredDeviceId;
    private final String hostname;
    // one per extractor: every extract() gives the same device id
    private final String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, SUFFIX_LENGTH);

    /**
     * @param configuredDeviceId the device id to use as is; null or blank to find one
     * @param hostname           the last resort
     */
    public DeviceIdExtractor(@Nullable String configuredDeviceId, String hostname) {
        this.configuredDeviceId = configuredDeviceId;
        this.hostname = hostname;
    }

    public String extract() {
        return extract(configuredDeviceId, MOUNT_INFO, hostname, randomSuffix);
    }

    static String extract(@Nullable String configuredDeviceId, Path mountInfo, String hostname, String randomSuffix) {
        if (StringUtils.isNotBlank(configuredDeviceId)) {
            return configuredDeviceId.strip();
        }
        String mounts = readMountInfo(mountInfo);
        return find(DOCKER_CONTAINER_ID, mounts)
                .orElseGet(() -> find(KUBERNETES_POD_UID, mounts).orElse(hostname) + "-" + randomSuffix);
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
