package com.sunnyblog.digitalhuman.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmotionParser {

    private static final Pattern EMOTION_PATTERN =
            Pattern.compile("\\[emotion:\\s*([^\\]]+?)\\s*]", Pattern.CASE_INSENSITIVE);
    private static final String DEFAULT_EMOTION = "neutral";

    private EmotionParser() {
    }

    public static ParseResult parse(String input) {
        if (input == null || input.isBlank()) {
            return new ParseResult("", DEFAULT_EMOTION);
        }
        Matcher matcher = EMOTION_PATTERN.matcher(input);
        String emotion = DEFAULT_EMOTION;
        while (matcher.find()) {
            emotion = matcher.group(1).trim().toLowerCase();
        }
        String cleaned = EMOTION_PATTERN.matcher(input).replaceAll("").trim();
        cleaned = cleaned.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n");
        return new ParseResult(cleaned, emotion);
    }

    public record ParseResult(String text, String emotion) {
    }
}
