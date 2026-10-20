package com.sunnyblog.agent;

/**
 * Per-execution isolation context. A session owns memory; a run/task owns one
 * clean execution, its tool selection and its artifact directory.
 */
public final class AgentTaskContext {

    public record Task(String sessionId, String runId) {
    }

    private static final ThreadLocal<Task> CURRENT = new ThreadLocal<>();

    private AgentTaskContext() {
    }

    public static void set(String sessionId, String runId) {
        CURRENT.set(new Task(sessionId, runId));
    }

    public static Task current() {
        return CURRENT.get();
    }

    public static String runId() {
        Task task = CURRENT.get();
        return task == null ? null : task.runId();
    }

    public static String sessionId() {
        Task task = CURRENT.get();
        return task == null ? null : task.sessionId();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
