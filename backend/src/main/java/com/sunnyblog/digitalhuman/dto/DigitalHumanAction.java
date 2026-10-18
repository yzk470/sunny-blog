package com.sunnyblog.digitalhuman.dto;

public record DigitalHumanAction(
        String action,
        String emotion,
        String motionPrompt
) {
    public static DigitalHumanAction fallback(String emotion) {
        return new DigitalHumanAction("nod", emotion == null ? "neutral" : emotion, "");
    }
}
