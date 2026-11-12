package com.comagents.agentproxy.workspace;

import com.comagents.agentproxy.domain.AiAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionFoldersTest {

    @TempDir
    Path tempDir;

    @Test
    void createsRootAndBuildsSessionPathsUnderIt() {
        Path root = tempDir.resolve("var/lib/agent-proxy/sessions");
        UUID sessionId = UUID.randomUUID();

        SessionFolders sessionFolders = new SessionFolders(root.toString());

        assertTrue(Files.isDirectory(root));
        assertEquals(root, sessionFolders.root());
        assertEquals(root.resolve(sessionId.toString()), sessionFolders.of(sessionId));
        assertEquals(root.resolve(sessionId + "/code_review"), sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW));
        assertEquals(root.resolve(sessionId + "/code_review/agent-response-" + sessionId + ".md"),
                sessionFolders.agentResponseFile(sessionId, AiAgent.CODE_REVIEW));
    }

    @Test
    void unusableRootFailsAtStartup() throws IOException {
        Path file = Files.writeString(tempDir.resolve("not-a-folder"), "x");

        assertThrows(UncheckedIOException.class, () -> new SessionFolders(file.resolve("sessions").toString()));
    }
}
