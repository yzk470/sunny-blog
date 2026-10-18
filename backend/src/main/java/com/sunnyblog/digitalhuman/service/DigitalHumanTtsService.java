package com.sunnyblog.digitalhuman.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunnyblog.digitalhuman.config.AliyunTtsProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@Service
public class DigitalHumanTtsService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AliyunTtsProperties properties;
    private final OkHttpClient httpClient;
    private final Path audioDir;

    public DigitalHumanTtsService(AliyunTtsProperties properties,
                                  @Value("${app.digital-human.audio-dir:./data/digital-human/audio}") String audioDir) {
        this.properties = properties;
        this.audioDir = Path.of(audioDir).toAbsolutePath().normalize();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(audioDir);
        log.info("DigitalHumanTtsService ready: url={}, model={}, voice={}, key={}, dir={}",
                properties.getApiUrl(), properties.getModel(), properties.getVoice(),
                properties.maskedKey(), audioDir);
    }

    /**
     * Synthesize speech and store it as a local WAV. Returns a public URL or an error message.
     */
    public TtsResult synthesize(String text, String voice, String model, Integer sampleRate) {
        if (text == null || text.isBlank()) {
            return TtsResult.error("语音内容为空");
        }
        if (text.trim().length() < 5) {
            return TtsResult.error("语音文本太短");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return TtsResult.error("未配置阿里云语音服务密钥");
        }
        String effectiveVoice = voice == null || voice.isBlank() ? properties.getVoice() : voice;
        String effectiveModel = model == null || model.isBlank() ? properties.getModel() : model;
        int effectiveRate = sampleRate == null || sampleRate <= 0 ? properties.getSampleRate() : sampleRate;
        try {
            byte[] wav = synthesizeViaWebSocket(text, effectiveVoice, effectiveModel, effectiveRate);
            if (wav == null || wav.length < 100) {
                return TtsResult.error("语音服务返回了空音频");
            }
            String filename = "dh_" + UUID.randomUUID().toString().replace("-", "") + ".wav";
            Files.write(audioDir.resolve(filename), wav);
            return TtsResult.ok("/api/digital-human/audio/" + filename);
        } catch (Exception ex) {
            log.warn("Digital human TTS failed: {}", ex.getMessage());
            return TtsResult.error("语音合成暂时不可用");
        }
    }

    private byte[] synthesizeViaWebSocket(String text, String voice, String model, int sampleRate) throws Exception {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
        CountDownLatch done = new CountDownLatch(1);
        Exception[] error = {null};

        Request request = new Request.Builder()
                .url(properties.getApiUrl())
                .header("Authorization", "Bearer " + properties.getApiKey())
                .build();

        WebSocket socket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                try {
                    webSocket.send(buildRunTask(taskId, text, voice, model, sampleRate));
                } catch (Exception ex) {
                    error[0] = ex;
                    done.countDown();
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String message) {
                try {
                    JsonNode root = MAPPER.readTree(message);
                    String event = root.path("header").path("event").asText("");
                    String outputType = root.path("payload").path("output").path("type").asText("");
                    if ("sentence-end".equals(outputType)) {
                        webSocket.close(1000, "done");
                    } else if ("task-failed".equals(event)) {
                        String detail = root.path("header").path("error_message").asText("TTS task failed");
                        error[0] = new IllegalStateException(detail);
                        done.countDown();
                    }
                } catch (Exception ex) {
                    log.debug("TTS message parse skipped: {}", ex.getMessage());
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                try {
                    audioBuffer.write(bytes.toByteArray());
                } catch (IOException ex) {
                    error[0] = ex;
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
                error[0] = throwable instanceof Exception ex ? ex : new Exception(throwable);
                done.countDown();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                done.countDown();
            }
        });

        if (!done.await(45, TimeUnit.SECONDS)) {
            socket.close(1000, "timeout");
            throw new IllegalStateException("语音合成超时");
        }
        if (error[0] != null) {
            throw error[0];
        }
        return audioBuffer.toByteArray();
    }

    private String buildRunTask(String taskId, String text, String voice, String model, int sampleRate) throws Exception {
        return buildRunTask(taskId, text, voice, model, sampleRate, properties.getResponseFormat());
    }

    private String buildRunTask(String taskId, String text, String voice, String model, int sampleRate,
                                String format) throws Exception {
        var root = MAPPER.createObjectNode();
        var header = MAPPER.createObjectNode();
        header.put("task_id", taskId);
        header.put("streaming", "duplex");
        header.put("action", "run-task");
        root.set("header", header);

        var payload = MAPPER.createObjectNode();
        payload.put("model", model);
        payload.put("function", "SpeechSynthesizer");
        payload.put("task_group", "audio");
        payload.put("task", "tts");
        var input = MAPPER.createObjectNode();
        input.put("text", text);
        payload.set("input", input);
        var parameters = MAPPER.createObjectNode();
        parameters.put("voice", voice);
        parameters.put("format", format);
        parameters.put("sample_rate", sampleRate);
        payload.set("parameters", parameters);
        root.set("payload", payload);
        return MAPPER.writeValueAsString(root);
    }

    public record TtsResult(String audioUrl, String error) {
        public static TtsResult ok(String url) {
            return new TtsResult(url, null);
        }

        public static TtsResult error(String message) {
            return new TtsResult(null, message);
        }
    }

    /**
     * Open a single DashScope streaming session. Text can be appended while
     * PCM chunks are delivered to the callback, so one continuous voice is
     * produced instead of separate WAV files per sentence.
     */
    public StreamingTtsSession openStream(String voice, String model, Integer sampleRate,
                                          String initialText, Consumer<byte[]> onPcm,
                                          Consumer<String> onError) {
        String effectiveVoice = voice == null || voice.isBlank() ? properties.getVoice() : voice;
        String effectiveModel = model == null || model.isBlank() ? properties.getModel() : model;
        int effectiveRate = sampleRate == null || sampleRate <= 0 ? properties.getSampleRate() : sampleRate;
        return new StreamingTtsSession(effectiveVoice, effectiveModel, effectiveRate,
                initialText == null ? "" : initialText, onPcm, onError);
    }

    public final class StreamingTtsSession {
        private final String taskId = UUID.randomUUID().toString().replace("-", "");
        private final String voice;
        private final String model;
        private final int sampleRate;
        private final Consumer<byte[]> onPcm;
        private final Consumer<String> onError;
        private final CountDownLatch done = new CountDownLatch(1);
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final AtomicBoolean finishRequested = new AtomicBoolean(false);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final StringBuilder pending = new StringBuilder();
        private final WebSocket socket;

        private StreamingTtsSession(String voice, String model, int sampleRate, String initialText,
                                    Consumer<byte[]> onPcm, Consumer<String> onError) {
            this.voice = voice;
            this.model = model;
            this.sampleRate = sampleRate;
            this.onPcm = onPcm;
            this.onError = onError;
            Request request = new Request.Builder()
                    .url(properties.getApiUrl())
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .build();
            this.socket = httpClient.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    try {
                        webSocket.send(buildRunTask(taskId, initialText, voice, model, sampleRate, "pcm"));
                    } catch (Exception ex) {
                        fail(ex.getMessage());
                    }
                }

                @Override
                public void onMessage(WebSocket webSocket, String message) {
                    try {
                        JsonNode root = MAPPER.readTree(message);
                        String event = root.path("header").path("event").asText("");
                        if ("task-started".equals(event)) {
                            started.set(true);
                            synchronized (pending) {
                                if (!pending.isEmpty()) {
                                    webSocket.send(buildContinueTask(taskId, pending.toString()));
                                    pending.setLength(0);
                                }
                            }
                            if (finishRequested.get()) {
                                webSocket.send(buildFinishTask(taskId));
                            }
                        } else if ("task-failed".equals(event)) {
                            fail(root.path("header").path("error_message").asText("TTS task failed"));
                        } else if ("task-finished".equals(event)) {
                            closed.set(true);
                            webSocket.close(1000, "done");
                            done.countDown();
                        }
                    } catch (Exception ex) {
                        log.debug("TTS stream message parse skipped: {}", ex.getMessage());
                    }
                }

                @Override
                public void onMessage(WebSocket webSocket, ByteString bytes) {
                    if (onPcm != null && !closed.get()) {
                        try {
                            onPcm.accept(bytes.toByteArray());
                        } catch (Exception ex) {
                            log.warn("TTS PCM callback failed: {}", ex.getMessage());
                        }
                    }
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
                    fail(throwable == null ? "TTS connection failed" : throwable.getMessage());
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    closed.set(true);
                    done.countDown();
                }
            });
        }

        public void append(String text) {
            if (text == null || text.isBlank() || finished()) return;
            if (!started.get()) {
                synchronized (pending) {
                    pending.append(text);
                }
                return;
            }
            socket.send(buildContinueTask(taskId, text));
        }

        public void finish() {
            if (finished()) return;
            finishRequested.set(true);
            if (started.get()) {
                socket.send(buildFinishTask(taskId));
            }
        }

        public boolean awaitFinished(long timeout, TimeUnit unit) throws InterruptedException {
            return done.await(timeout, unit);
        }

        public void close() {
            if (finished()) return;
            closed.set(true);
            socket.close(1000, "client closed");
            done.countDown();
        }

        private boolean finished() {
            return closed.get() || done.getCount() == 0;
        }

        private void fail(String message) {
            if (closed.compareAndSet(false, true)) {
                if (onError != null) {
                    onError.accept(message == null ? "TTS failed" : message);
                }
                socket.cancel();
                done.countDown();
            }
        }
    }

    private String buildContinueTask(String taskId, String text) {
        try {
            var root = MAPPER.createObjectNode();
            var header = MAPPER.createObjectNode();
            header.put("task_id", taskId);
            header.put("streaming", "duplex");
            header.put("action", "continue-task");
            root.set("header", header);
            var payload = MAPPER.createObjectNode();
            var input = MAPPER.createObjectNode();
            input.put("text", text);
            payload.set("input", input);
            root.set("payload", payload);
            return MAPPER.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String buildFinishTask(String taskId) {
        try {
            var root = MAPPER.createObjectNode();
            var header = MAPPER.createObjectNode();
            header.put("task_id", taskId);
            header.put("streaming", "duplex");
            header.put("action", "finish-task");
            root.set("header", header);
            var payload = MAPPER.createObjectNode();
            payload.set("input", MAPPER.createObjectNode());
            root.set("payload", payload);
            return MAPPER.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
