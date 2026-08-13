package io.github.htearih.scribble.bot.text;

import java.util.regex.Pattern;

/** Helpers for the mention text scribble.pub delivers. */
public final class Mentions {

    private static final Pattern REPEATED_SPACE = Pattern.compile("\\s{2,}");

    private Mentions() {
    }

    /**
     * Removes {@code @handle} (any case, plus a trailing comma or colon) from the message, so a
     * handler sees what was actually said rather than the vocative.
     *
     * <p>Word-bounded: {@code @maryanne} survives a handle of {@code mary}.
     */
    public static String stripHandle(String text, String handle) {
        if (handle == null || handle.isBlank()) {
            return text == null ? "" : text.trim();
        }
        return strip(text, pattern(handle));
    }

    /**
     * As {@link #stripHandle(String, String)}, with the pattern compiled once up front. Named
     * apart from the overload so a {@code null} argument stays unambiguous at the call site.
     */
    public static String strip(String text, Pattern handlePattern) {
        if (text == null) {
            return "";
        }
        var stripped = handlePattern.matcher(text).replaceAll(" ");
        return REPEATED_SPACE.matcher(stripped).replaceAll(" ").trim();
    }

    /** The pattern {@link #stripHandle(String, String)} and {@link #strip(String, Pattern)} match. */
    public static Pattern pattern(String handle) {
        return Pattern.compile("(?i)@" + Pattern.quote(handle.trim()) + "\\b[,:]?");
    }
}
