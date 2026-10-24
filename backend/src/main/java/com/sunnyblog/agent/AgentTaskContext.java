package com.sunnyblog.agent;

/**
 * Per-execution isolation context. A session owns memory; a run/task owns one
 * clean execution, its tool selection and its artifact directory.
 */
public final class AgentTaskContext {

    public record Task(String sessionId, String runId, String attemptId) {
    }

    private static final ThreadLocal<Task> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<String> BLOCKED = new ThreadLocal<>();

    private AgentTaskContext() {
    }

    public static void set(String sessionId, String runId) {
        set(sessionId, runId, null);
    }

    public static void set(String sessionId, String runId, String attemptId) {
        CURRENT.set(new Task(sessionId, runId, attemptId));
        BLOCKED.remove();
    }

    public static void block(String reason) {
        BLOCKED.set(reason == null || reason.isBlank() ? "UNSAT" : reason);
    }

    public static String blockedReason() {
        return BLOCKED.get();
    }

    public static boolean isBlocked() {
        return BLOCKED.get() != null;
    }

    public static Task current() {
        return CURRENT.get();
    }

    public static String runId() {
        Task task = CURRENT.get();
        return task == null ? null : task.runId();
    }

    public static String attemptId() {
        Task task = CURRENT.get();
        return task == null ? null : task.attemptId();
    }

    public static String sessionId() {
        Task task = CURRENT.get();
        return task == null ? null : task.sessionId();
    }

    public static void clear() {
        CURRENT.remove();
        BLOCKED.remove();
    }
}
