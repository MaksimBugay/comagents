package com.comagents.commons.communication;

import com.comagents.commons.domain.AiHarnessProvider;

/** The workspace, accounts and applications comagents talk over. */
public final class CommunicationUtils {

    public static final String COMAGENTS_WORKSPACE_ID = "comagents-main";

    private static final String AGENT_EXECUTOR_APPLICATION_ID_TEMPLATE = "%s_EXECUTOR";
    public static final String AGENT_EXECUTOR_ACCOUNT_ID = "agent-executor@comagents.com";

    public static final String ORCHESTRATOR_APPLICATION_ID = "AGENT_ORCHESTRATOR";
    public static final String ORCHESTRATOR_ACCOUNT_ID = "agent-orchestrator@comagents.com";

    private CommunicationUtils() {
    }

    public static String buildAgentExecutorApplicationId(AiHarnessProvider harnessProvider) {
        return AGENT_EXECUTOR_APPLICATION_ID_TEMPLATE.formatted(harnessProvider.name());
    }
}
