package com.sunnyblog.agent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process cancellation signal. Status is also persisted in the run file,
 * but the executor holds its own run instance, so this flag is what actually
 * interrupts the next checkpoint boundary.
 */
public final class AgentTaskControl {

    private static final Set<String> CANCELLED = ConcurrentHashMap.newKeySet();

    private AgentTaskControl() {
    }

    public static void cancel(String runId) {
        if (runId != null) CANCELLED.add(runId);
    }

    public static boolean isCancelled(String runId) {
        return runId != null && CANCELLED.contains(runId);
    }

    public static void clear(String runId) {
        if (runId != null) CANCELLED.remove(runId);
    }
}
