package com.comagents.commons.domain;

import java.util.Locale;

public enum AiHarnessProvider {
    CLAUDE_CODE, CODEX;

    public String getHarnessName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
