package com.sunnyblog.digitalhuman.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunnyblog.digitalhuman.dto.DhChatRequest;
import com.sunnyblog.digitalhuman.model.DhCharacter;
import com.sunnyblog.digitalhuman.model.DhMessage;
import com.sunnyblog.digitalhuman.model.DhSession;
import com.sunnyblog.digitalhuman.util.EmotionParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DigitalHumanStreamService {

    private static final Pattern SPEECH_BOUNDARY = Pattern.compile("[。！？!?；;，,\\n]");
    private static final Pattern MOTION_TAG =
            Pattern.compile("\\[\\[motion:([^,\\]]+)(?:,emotion:([^\\]]+))?]]");
    private static final Set<String> MOTION_KEYS = Set.of(
            "wave", "salute", "nod", "bow", "think", "happy", "shy", "stretch", "shrug",
            "cheer", "letsgo", "handclap", "spin", "warn", "calm", "lonely", "beckon", "rubhands");

    private final DigitalHumanChatService chatService;
    private final DigitalHumanSessionService sessionService;
    private final DigitalHumanCharacterService characterService;
    private final ObjectMapper objectMapper;

    @Value("${app.digital-human.max-history:10}")
    private int maxHistory;

    public void stream(DhChatRequest request, SseEmitter emitter) {
        Thread.startVirtualThread(() -> run(request, emitter));
    }

    private void run(DhChatRequest request, SseEmitter emitter) {
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicReference<DigitalHumanTtsService.StreamingTtsSession> ttsRef = new AtomicReference<>();
        emitter.onCompletion(() -> {
            closed.set(true);
            closeTts(ttsRef);
        });
        emitter.onTimeout(() -> {
            closed.set(true);
            closeTts(ttsRef);
        });
        emitter.onError((error) -> {
            closed.set(true);
            closeTts(ttsRef);
        });

        String replyText = "";
        AtomicReference<String> emotion = new AtomicReference<>("neutral");
        DhSession session = null;

        try {
            session = sessionService.requireSession(request.sessionId());
            DhCharacter character = characterService.getByCode(session.getCharacterCode());
            if (character == null) {
                throw new com.sunnyblog.common.BusinessException("数字人角色已经下架");
            }

            List<DhMessage> history = sessionService.history(session.getId(), maxHistory);
            sessionService.saveUserMessage(session.getId(), request.content());

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("sessionId", session.getId());
            meta.put("action", "none");
            meta.put("emotion", "neutral");
            meta.put("motionPrompt", "");
            meta.put("motionStreaming", true);
            meta.put("audioStreaming", true);
            meta.put("audioFormat", "pcm");
            meta.put("audioSampleRate", character.getTtsSampleRate() == null ? 24000 : character.getTtsSampleRate());
            send(emitter, closed, "meta", meta);

            StringBuilder full = new StringBuilder();
            StringBuilder speechBuffer = new StringBuilder();
            AtomicBoolean ttsStarted = new AtomicBoolean(false);
            AtomicInteger pcmSeq = new AtomicInteger();
            AtomicInteger motionSeq = new AtomicInteger();
            int sampleRate = character.getTtsSampleRate() == null ? 24000 : character.getTtsSampleRate();
            String characterCode = character.getCharacterCode();

            Consumer<String> textConsumer = text -> {
                if (text == null || text.isEmpty() || closed.get()) return;
                String cleanText = text;
                if (cleanText.isEmpty()) return;
                full.append(cleanText);
                Map<String, Object> delta = new LinkedHashMap<>();
                delta.put("text", cleanText);
                send(emitter, closed, "delta", delta);

                speechBuffer.append(cleanText);
                String speech = stripForSpeech(speechBuffer.toString());
                if (!ttsStarted.get()) {
                    if (speech.length() >= 12 || (speech.length() >= 5 && SPEECH_BOUNDARY.matcher(speech).find())) {
                        startTts(characterCode, speech, emitter, closed, pcmSeq, sampleRate, ttsRef);
                        ttsStarted.set(true);
                        speechBuffer.setLength(0);
                    }
                } else if (speech.length() >= 24 || SPEECH_BOUNDARY.matcher(speech).find()) {
                    DigitalHumanTtsService.StreamingTtsSession tts = ttsRef.get();
                    if (tts != null) tts.append(speech);
                    speechBuffer.setLength(0);
                }
            };
            LegacyTagFilter legacyTagFilter = new LegacyTagFilter(textConsumer);

            Consumer<MotionSignal> motionConsumer = signal -> {
                String motion = normalizeMotion(signal.motion());
                if (motion == null) return;
                String normalizedEmotion = chatService.normalizeEmotion(signal.emotion());
                emotion.set(normalizedEmotion);
                Map<String, Object> motionEvent = new LinkedHashMap<>();
                motionEvent.put("seq", motionSeq.incrementAndGet());
                motionEvent.put("motion", motion);
                motionEvent.put("emotion", normalizedEmotion);
                send(emitter, closed, "motion", motionEvent);
            };

            MotionStreamParser parser = new MotionStreamParser(legacyTagFilter::accept, motionConsumer);
            Flux<String> tokens = chatService.streamReply(
                    character, history, request.content().trim(), request.articleSlug());

            tokens.doOnNext(parser::accept).blockLast(Duration.ofMinutes(5));
            parser.finish();
            legacyTagFilter.finish();

            String tail = stripForSpeech(speechBuffer.toString());
            if (!tail.isBlank()) {
                if (!ttsStarted.get()) {
                    startTts(characterCode, tail, emitter, closed, pcmSeq, sampleRate, ttsRef);
                    ttsStarted.set(true);
                } else {
                    DigitalHumanTtsService.StreamingTtsSession tts = ttsRef.get();
                    if (tts != null) tts.append(tail);
                }
            }

            DigitalHumanTtsService.StreamingTtsSession tts = ttsRef.get();
            if (tts != null) tts.finish();

            replyText = cleanReply(full.toString());
            if (replyText.isBlank()) {
                replyText = "我听见了，只是现在还没想好该怎么回答。";
            }

            DhMessage saved = sessionService.saveAssistantMessage(
                    session.getId(), replyText, emotion.get(), null);

            Map<String, Object> done = new LinkedHashMap<>();
            done.put("messageId", saved.getId());
            done.put("sessionId", session.getId());
            done.put("reply", replyText);
            done.put("emotion", emotion.get());
            done.put("action", "none");
            send(emitter, closed, "done", done);

            // The text is complete and the input is usable; keep the SSE
            // connection open until the last PCM chunk has been delivered.
            if (tts != null) {
                try {
                    tts.awaitFinished(90, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    log.warn("Waiting for streaming TTS failed: {}", ex.getMessage());
                }
            }
            emitter.complete();
        } catch (Exception ex) {
            log.error("Streaming digital-human chat failed: {}", ex.getMessage(), ex);
            closeTts(ttsRef);
            try {
                if (session != null && replyText.isBlank()) {
                    replyText = "抱歉，我的思绪刚刚被云遮住了。请稍后再试。";
                    sessionService.saveAssistantMessage(session.getId(), replyText, "sad", null);
                }
                if (!closed.get()) {
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("message", ex.getMessage() == null
                            ? "AI 服务连接失败，请稍后重试"
                            : ex.getMessage());
                    send(emitter, closed, "error", error);
                }
                emitter.complete();
            } catch (Exception ignored) {
                // Client may already be gone.
            }
        }
    }

    private void startTts(String characterCode, String text, SseEmitter emitter, AtomicBoolean closed,
                          AtomicInteger seq, int sampleRate,
                          AtomicReference<DigitalHumanTtsService.StreamingTtsSession> ttsRef) {
        if (text == null || text.isBlank() || ttsRef.get() != null) return;
        try {
            DigitalHumanTtsService.StreamingTtsSession tts = chatService.openSpeechStream(
                    characterCode, text,
                    pcm -> {
                        if (closed.get()) return;
                        Map<String, Object> audio = new LinkedHashMap<>();
                        audio.put("seq", seq.incrementAndGet());
                        audio.put("sampleRate", sampleRate);
                        audio.put("pcm", Base64.getEncoder().encodeToString(pcm));
                        send(emitter, closed, "audio-pcm", audio);
                    },
                    error -> {
                        Map<String, Object> audioError = new LinkedHashMap<>();
                        audioError.put("message", error);
                        send(emitter, closed, "audio-pcm-error", audioError);
                    });
            ttsRef.set(tts);
        } catch (Exception ex) {
            log.warn("Failed to start streaming TTS: {}", ex.getMessage());
            Map<String, Object> audioError = new LinkedHashMap<>();
            audioError.put("message", "语音合成暂时不可用");
            send(emitter, closed, "audio-pcm-error", audioError);
        }
    }

    private void closeTts(AtomicReference<DigitalHumanTtsService.StreamingTtsSession> ttsRef) {
        DigitalHumanTtsService.StreamingTtsSession tts = ttsRef.getAndSet(null);
        if (tts != null) tts.close();
    }

    private String normalizeMotion(String raw) {
        if (raw == null) return null;
        String value = raw.trim().toLowerCase();
        String key = switch (value) {
            case "wave", "挥手", "手を振る" -> "wave";
            case "salute", "敬礼" -> "salute";
            case "nod", "点头" -> "nod";
            case "bow", "鞠躬", "お辞儀" -> "bow";
            case "think", "思考", "抱臂", "腕組み" -> "think";
            case "happy", "开心", "高兴", "拍手" -> "happy";
            case "shy", "害羞", "脸红" -> "shy";
            case "stretch", "伸懒腰", "伸び" -> "stretch";
            case "shrug", "摊手", "无奈" -> "shrug";
            case "cheer", "声援" -> "cheer";
            case "letsgo", "出发", "レッツゴー" -> "letsgo";
            case "handclap", "打拍子", "手拍子" -> "handclap";
            case "spin", "转手", "ろくろを回す" -> "spin";
            case "warn", "提醒", "注意喚起" -> "warn";
            case "calm", "安抚", "落ち着かせる" -> "calm";
            case "lonely", "寂寞", "寂しがる" -> "lonely";
            case "beckon", "招手", "手招き" -> "beckon";
            case "rubhands", "搓手", "揉み手" -> "rubhands";
            default -> null;
        };
        return key != null && MOTION_KEYS.contains(key) ? key : null;
    }

    private record MotionSignal(String motion, String emotion) {
    }

    /**
     * Removes legacy [emotion:...] / [action:...] tags even when the LLM
     * streams them one character at a time.
     */
    private static final class LegacyTagFilter {
        private static final String[] PREFIXES = {"[emotion:", "[action:"};
        private final Consumer<String> textConsumer;
        private final StringBuilder buffer = new StringBuilder();

        private LegacyTagFilter(Consumer<String> textConsumer) {
            this.textConsumer = textConsumer;
        }

        private void accept(String chunk) {
            if (chunk == null || chunk.isEmpty()) return;
            buffer.append(chunk);
            drain();
        }

        private void finish() {
            if (buffer.length() > 0) {
                textConsumer.accept(buffer.toString());
                buffer.setLength(0);
            }
        }

        private void drain() {
            while (true) {
                int start = buffer.indexOf("[");
                if (start < 0) {
                    textConsumer.accept(buffer.toString());
                    buffer.setLength(0);
                    return;
                }
                if (start > 0) {
                    textConsumer.accept(buffer.substring(0, start));
                    buffer.delete(0, start);
                    continue;
                }
                String current = buffer.toString();
                boolean possiblePrefix = false;
                for (String prefix : PREFIXES) {
                    if (prefix.startsWith(current) && current.length() < prefix.length()) {
                        possiblePrefix = true;
                        break;
                    }
                }
                if (possiblePrefix) return;
                boolean matches = false;
                for (String prefix : PREFIXES) {
                    if (current.startsWith(prefix)) {
                        matches = true;
                        break;
                    }
                }
                if (matches) {
                    int end = buffer.indexOf("]");
                    if (end < 0) return;
                    buffer.delete(0, end + 1);
                    continue;
                }
                textConsumer.accept("[");
                buffer.deleteCharAt(0);
            }
        }
    }

    /**
     * Incremental parser for [[motion:key,emotion:tag]] control tags. Text is
     * forwarded immediately, tags may be split across LLM tokens, and the
     * first tag can arrive before any visible text.
     */
    private static final class MotionStreamParser {
        private final Consumer<String> textConsumer;
        private final Consumer<MotionSignal> motionConsumer;
        private final StringBuilder buffer = new StringBuilder();

        private MotionStreamParser(Consumer<String> textConsumer, Consumer<MotionSignal> motionConsumer) {
            this.textConsumer = textConsumer;
            this.motionConsumer = motionConsumer;
        }

        private void accept(String chunk) {
            if (chunk == null || chunk.isEmpty()) return;
            buffer.append(chunk);
            drain();
        }

        private void finish() {
            drain();
            if (buffer.length() > 0) {
                textConsumer.accept(buffer.toString());
                buffer.setLength(0);
            }
        }

        private void drain() {
            while (true) {
                int start = buffer.indexOf("[[");
                if (start < 0) {
                    int keep = buffer.length() > 0 && buffer.charAt(buffer.length() - 1) == '[' ? 1 : 0;
                    int end = buffer.length() - keep;
                    if (end > 0) {
                        textConsumer.accept(buffer.substring(0, end));
                        buffer.delete(0, end);
                    }
                    return;
                }
                if (start > 0) {
                    textConsumer.accept(buffer.substring(0, start));
                    buffer.delete(0, start);
                    continue;
                }
                int end = buffer.indexOf("]]");
                if (end < 0) return;
                String candidate = buffer.substring(0, end + 2);
                Matcher matcher = MOTION_TAG.matcher(candidate);
                if (matcher.matches()) {
                    motionConsumer.accept(new MotionSignal(matcher.group(1), matcher.group(2)));
                    buffer.delete(0, end + 2);
                    continue;
                }
                textConsumer.accept("[");
                buffer.deleteCharAt(0);
            }
        }
    }

    private String stripForSpeech(String text) {
        return text
                .replaceAll("\\[emotion:[^]]*]", "")
                .replaceAll("\\[action:[^]]*]", "")
                .replaceAll("[*_`#>]", "")
                .replaceAll("[\\p{So}\\p{Cs}\\p{Cn}]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void send(SseEmitter emitter, AtomicBoolean closed, String event, Object data) {
        if (closed.get()) return;
        try {
            synchronized (emitter) {
                if (closed.get()) return;
                emitter.send(SseEmitter.event()
                        .name(event)
                        .data(objectMapper.writeValueAsString(data), MediaType.APPLICATION_JSON));
            }
        } catch (IOException | IllegalStateException ex) {
            closed.set(true);
            log.debug("SSE client disconnected: {}", ex.getMessage());
        }
    }

    private String cleanReply(String raw) {
        EmotionParser.ParseResult parsed = EmotionParser.parse(raw);
        return parsed.text()
                .replaceAll("[\\p{So}\\p{Cs}\\p{Cn}]", "")
                .replaceAll("[ \\t]+", " ")
                .trim();
    }
}
