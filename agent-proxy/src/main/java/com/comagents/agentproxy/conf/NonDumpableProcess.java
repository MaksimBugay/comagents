package com.comagents.agentproxy.conf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Makes agent-proxy's process non-dumpable ({@code prctl(PR_SET_DUMPABLE, 0)}, Linux): the agents' processes, which
 * run as the same user, can then no longer read its {@code /proc/<pid>/environ} (the Pushca key, the sources token)
 * or attach to it. The processes it starts are dumpable again: {@code execve} resets the flag.
 */
public final class NonDumpableProcess {

    private static final Logger LOG = LoggerFactory.getLogger(NonDumpableProcess.class);

    private static final int PR_SET_DUMPABLE = 4;

    private NonDumpableProcess() {
    }

    /**
     * Only on Linux: elsewhere (development on macOS) there is no prctl and no agent to keep out.
     *
     * @throws IllegalStateException when prctl fails: agent-proxy must not run with its environment readable
     */
    public static void apply() {
        if (!System.getProperty("os.name").startsWith("Linux")) {
            LOG.debug("Not on Linux, the process stays dumpable");
            return;
        }
        int result = prctl(PR_SET_DUMPABLE, 0);
        if (result != 0) {
            throw new IllegalStateException("prctl(PR_SET_DUMPABLE, 0) failed with " + result);
        }
        LOG.info("Process made non-dumpable: agents can't read its environment");
    }

    // int prctl(int option, ...): variadic after the option, so the second argument is passed as a long
    @SuppressWarnings("SameParameterValue")
    private static int prctl(int option, long argument) {
        Linker linker = Linker.nativeLinker();
        MethodHandle prctl = linker.downcallHandle(
                linker.defaultLookup().find("prctl").orElseThrow(() -> new IllegalStateException("No prctl in libc")),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG),
                Linker.Option.firstVariadicArg(1));
        try {
            return (int) prctl.invokeExact(option, argument);
        } catch (Throwable e) {
            throw new IllegalStateException("prctl(" + option + ", " + argument + ") failed", e);
        }
    }
}
