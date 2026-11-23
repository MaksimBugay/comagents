package com.comagents.commons.communication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceIdExtractorTest {

    private static final String CONTAINER_ID = "cb960a48877d8a7995f18bd400c563fd1eeb9810cf61ad584e4f2478e7e1abb3";
    private static final String POD_UID = "0f6c8a3e-2b1d-4c5e-9f7a-1a2b3c4d5e6f";
    private static final String SUFFIX = "a1b2c3d4e5f6";

    // as in a Docker (Docker Desktop, Swarm) container
    private static final String DOCKER_MOUNTS = """
            213 180 0:52 / / rw,relatime master:9 - overlay overlay rw,lowerdir=/var/lib/docker/overlay2/l/ABC
            222 213 254:1 /docker/containers/%1$s/hostname /etc/hostname rw,relatime - ext4 /dev/vda1 rw
            223 213 254:1 /docker/containers/%1$s/hosts /etc/hosts rw,relatime - ext4 /dev/vda1 rw
            """.formatted(CONTAINER_ID);

    // as in a Kubernetes pod with containerd: /etc/hostname is the sandbox's, not a Docker container's
    private static final String KUBERNETES_MOUNTS = """
            1180 1100 0:310 / / rw,relatime master:400 - overlay overlay rw
            1195 1180 259:1 /var/lib/kubelet/pods/%1$s/etc-hosts /etc/hosts rw,relatime - ext4 /dev/nvme0n1p1 rw
            1197 1180 259:1 /var/lib/containerd/io.containerd.grpc.v1.cri/sandboxes/%2$s/hostname /etc/hostname rw - ext4 /dev/nvme0n1p1 rw
            1199 1180 0:280 / /var/run/secrets/kubernetes.io/serviceaccount ro,relatime - tmpfs tmpfs rw
            """.formatted(POD_UID, "a".repeat(64));

    @TempDir
    Path dir;

    @Test
    void configuredDeviceIdWins() throws IOException {
        assertEquals("proxy-1", DeviceIdExtractor.extract(" proxy-1 ", mountInfo(DOCKER_MOUNTS), "host", SUFFIX));
    }

    @Test
    void dockerContainerId() throws IOException {
        assertEquals(CONTAINER_ID, DeviceIdExtractor.extract("", mountInfo(DOCKER_MOUNTS), "host", SUFFIX),
                "one container in the whole Swarm: no suffix");
    }

    @Test
    void kubernetesPodUid() throws IOException {
        assertEquals(POD_UID + "-" + SUFFIX, DeviceIdExtractor.extract(null, mountInfo(KUBERNETES_MOUNTS), "host", SUFFIX),
                "shared by the pod's containers");
    }

    @Test
    void hostnameOtherwise() throws IOException {
        assertEquals("host-" + SUFFIX,
                DeviceIdExtractor.extract(" ", mountInfo("213 180 0:52 / / rw - overlay overlay rw\n"), "host", SUFFIX));
        assertEquals("host-" + SUFFIX, DeviceIdExtractor.extract(null, dir.resolve("no-mountinfo"), "host", SUFFIX));
    }

    @Test
    void oneExtractorIsOneDevice() {
        DeviceIdExtractor extractor = new DeviceIdExtractor(null, "host");

        assertEquals(extractor.extract(), extractor.extract());
    }

    private Path mountInfo(String content) throws IOException {
        return Files.writeString(dir.resolve("mountinfo"), content);
    }
}
