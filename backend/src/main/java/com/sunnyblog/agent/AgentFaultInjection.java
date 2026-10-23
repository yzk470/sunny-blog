package com.sunnyblog.agent;

/**
 * Benchmark-only deterministic fault injection. It is thread-local so it
 * cannot affect normal user tasks or other workers.
 *
 * ENVIRONMENT / TRANSIENT / TOOL / FORMAT -> first matching tool call fails
 * LOGIC -> first validator call fails
 * BUDGET -> task budget is clamped
 * UNSAT -> no synthetic failure; the benchmark task itself is impossible
 */
public final class AgentFaultInjection {

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private static final class State {
        private final String type;
        private boolean toolConsumed;
        private boolean validatorConsumed;

        private State(String type) {
            this.type = type == null ? "" : type.toUpperCase();
        }
    }

    private AgentFaultInjection() {
    }

    public static void start(String type) {
        if (type == null || type.isBlank()) {
            CURRENT.remove();
        } else {
            CURRENT.set(new State(type));
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static String type() {
        State state = CURRENT.get();
        return state == null ? "" : state.type;
    }

    public static String takeToolFailure(String action) {
        State state = CURRENT.get();
        if (state == null || state.toolConsumed) return null;
        String type = state.type;
        String error = switch (type) {
            case "ENVIRONMENT" -> {
                if ("run_script".equals(action) || "run_command".equals(action) || "write_file".equals(action)) {
                    yield "ModuleNotFoundError: simulated missing dependency";
                }
                yield null;
            }
            case "TRANSIENT" -> {
                if ("run_script".equals(action) || "run_command".equals(action)) {
                    yield "timeout: simulated transient network timeout";
                }
                yield null;
            }
            case "TOOL" -> {
                if ("write_file".equals(action) || "run_script".equals(action)) {
                    yield "Permission denied: simulated tool failure";
                }
                yield null;
            }
            case "FORMAT" -> "invalid JSON format: simulated malformed tool response";
            default -> null;
        };
        if (error != null) state.toolConsumed = true;
        return error;
    }

    public static boolean takeValidatorFailure() {
        State state = CURRENT.get();
        if (state == null || state.validatorConsumed || !"LOGIC".equals(state.type)) return false;
        state.validatorConsumed = true;
        return true;
    }

    public static int budgetOverride(int normal) {
        State state = CURRENT.get();
        return state != null && "BUDGET".equals(state.type) ? Math.min(normal, 8) : normal;
    }
}
