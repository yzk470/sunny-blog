package com.sunnyblog.agent;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs the Python benchmark harness as a managed background process so the
 * admin page can start/stop it and inspect progress without blocking HTTP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentBenchmarkService {

    private final AgentProperties properties;
    private final AtomicReference<Process> processRef = new AtomicReference<>();
    private final StringBuilder processLog = new StringBuilder();
    private volatile boolean running;
    private volatile int exitCode = -999;
    private volatile String startedAt;
    private volatile String finishedAt;

    public record Status(boolean running, int exitCode, String startedAt, String finishedAt,
                         String log, String report) {
    }

    public synchronized Status start(String variants, int repeats, String group, List<String> taskIds) {
        if (running) throw new IllegalStateException("基准测试正在运行");
        Path dir = Path.of(properties.getBenchmarkDir()).toAbsolutePath().normalize();
        Path script = dir.resolve("run_benchmark.py");
        if (!Files.isRegularFile(script)) {
            throw new IllegalStateException("基准脚本不存在: " + script);
        }
        String python = Files.exists(Path.of("/usr/bin/python3")) ? "python3" : "python";
        List<String> command = new java.util.ArrayList<>();
        command.add(python);
        command.add(script.toString());
        command.add("--variants");
        command.add(variants == null || variants.isBlank() ? "linear,static,dynamic" : variants);
        command.add("--repeats");
        command.add(String.valueOf(Math.max(1, repeats)));
        command.add("--group");
        command.add(group == null || group.isBlank() ? "all" : group);
        if (taskIds != null && !taskIds.isEmpty()) {
            command.add("--task-ids");
            command.add(String.join(",", taskIds));
        }
        command.add("--report-dir");
        command.add(dir.resolve("reports").toString());
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(dir.toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("BENCHMARK_BASE_URL", "http://127.0.0.1:8090");
        builder.environment().put("PYTHONUNBUFFERED", "1");
        processLog.setLength(0);
        exitCode = -999;
        startedAt = Instant.now().toString();
        finishedAt = "";
        try {
            Process process = builder.start();
            processRef.set(process);
            running = true;
            Thread reader = new Thread(() -> read(process), "agent-benchmark-reader");
            reader.setDaemon(true);
            reader.start();
            return status();
        } catch (Exception ex) {
            running = false;
            throw new IllegalStateException("启动基准测试失败: " + ex.getMessage(), ex);
        }
    }

    private void read(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (processLog) {
                    processLog.append(line).append('\n');
                    if (processLog.length() > 50000) processLog.delete(0, processLog.length() - 40000);
                }
            }
            exitCode = process.waitFor();
        } catch (Exception ex) {
            synchronized (processLog) {
                processLog.append("benchmark error: ").append(ex.getMessage()).append('\n');
            }
        } finally {
            running = false;
            finishedAt = Instant.now().toString();
            processRef.set(null);
        }
    }

    public void stop() {
        Process process = processRef.get();
        if (process != null) {
            process.destroy();
            running = false;
            finishedAt = Instant.now().toString();
        }
    }

    public Status status() {
        String output;
        synchronized (processLog) {
            output = processLog.toString();
        }
        String report = "";
        Path reportFile = Path.of(properties.getBenchmarkDir()).toAbsolutePath()
                .normalize().resolve("reports").resolve("latest.json");
        if (Files.isRegularFile(reportFile)) {
            try {
                report = Files.readString(reportFile);
            } catch (Exception ignored) {
            }
        }
        return new Status(running, exitCode, startedAt, finishedAt, output, report);
    }

    @PreDestroy
    public void shutdown() {
        stop();
    }
}
