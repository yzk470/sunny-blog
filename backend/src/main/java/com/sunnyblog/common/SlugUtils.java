package com.sunnyblog.common;

import java.text.Normalizer;
import java.util.Locale;

public final class SlugUtils {

    private SlugUtils() {
    }

    public static String base(String input, String fallbackPrefix) {
        if (input == null || input.isBlank()) {
            return fallbackPrefix + "-" + System.currentTimeMillis();
        }
        String normalized = Normalizer.normalize(input.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFKC);
        String slug = normalized.replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            return fallbackPrefix + "-" + System.currentTimeMillis();
        }
        return slug.length() > 80 ? slug.substring(0, 80) : slug;
    }
}
