package com.comagents.agentproxy;

import com.comagents.agentproxy.conf.NonDumpableProcess;
import io.micronaut.runtime.Micronaut;

public class Application {

    static void main(String[] args) {
        // before anything starts an agent
        NonDumpableProcess.apply();
        Micronaut.run(Application.class, args);
    }
}
