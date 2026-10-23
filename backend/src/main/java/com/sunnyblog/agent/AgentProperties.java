package com.sunnyblog.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.agent")
public class AgentProperties {
    private String dir = "./data/agent";
    private boolean executionEnabled = true;
    private int maxSteps = 10;
    private int commandTimeoutSeconds = 20;
    private int maxOutputChars = 12000;
    private String model = "deepseek-flash";
    private boolean systemdRun = false;
    private String systemdRuntimeDir = "/run/user/999";
    private String systemdRunner = "/usr/local/bin/sunny-agent-run";
    private boolean systemdPrivateNetwork = true;
    private boolean highValueSearchEnabled = true;
    private int highValueSampleCount = 4;
    private int highValueMaxBranches = 3;
    private int highValueTaskBudget = 80;
}
