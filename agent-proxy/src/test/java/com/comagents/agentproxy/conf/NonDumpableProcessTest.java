package com.comagents.agentproxy.conf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NonDumpableProcessTest {

    // the kernel hands a non-dumpable process's /proc entries to root, so other users' processes (also the same user's)
    // can't read them
    @Test
    @EnabledOnOs(OS.LINUX)
    void environmentBecomesUnreadableForOtherProcesses() throws Exception {
        NonDumpableProcess.apply();

        assertEquals("root", Files.getOwner(Path.of("/proc/self/environ")).getName());
    }

    @Test
    @EnabledOnOs(OS.MAC)
    void doesNothingOffLinux() {
        NonDumpableProcess.apply();
    }
}
