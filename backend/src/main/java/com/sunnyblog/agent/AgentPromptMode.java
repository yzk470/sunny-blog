package com.sunnyblog.agent;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime switch used by the prompt-cache A/B. The default can be overridden
 * through the admin endpoint without restarting the service.
 */
@Component
public class AgentPromptMode {

    private final AtomicBoolean cacheFriendly = new AtomicBoolean(true);

    public boolean cacheFriendly() {
        return cacheFriendly.get();
    }

    public boolean setCacheFriendly(boolean enabled) {
        return cacheFriendly.getAndSet(enabled);
    }
}
