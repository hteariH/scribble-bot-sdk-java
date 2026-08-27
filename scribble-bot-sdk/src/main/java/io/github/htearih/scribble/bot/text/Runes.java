package io.github.htearih.scribble.bot.text;

/**
 * Translating between Java string indices (UTF-16 {@code char}s) and the platform's offsets
 * (<em>runes</em>, i.e. Unicode code points).
 *
 * <p>Quote offsets on the wire — {@link io.github.htearih.scribble.bot.model.RepliedMessage#quoteStart()}
 * and {@link io.github.htearih.scribble.bot.model.OutboundReplyTarget#quoteStart()} — count code
 * points, the way Go indexes runes. Java, like JavaScript, indexes UTF-16 code units, where every
 * astral character (most emoji) counts as two. {@link String#indexOf} and {@link String#substring}
 * therefore drift one position further right for every emoji earlier in the message, and a quote
 * built from them lands on the wrong fragment — or splits a surrogate pair and mangles it.
 *
 * <p>So: {@link #quoteRange} and {@link #slice} for quotes, {@link #toRuneOffset} and
 * {@link #toCharOffset} to convert an offset you already hold.
 */
public final class Runes {

    private Runes() {
    }

    /**
     * A quote's position and extent, both in runes — exactly the pair
     * {@link io.github.htearih.scribble.bot.model.OutboundReplyTarget} puts on the wire.
     */
    public record QuoteRange(int quoteStart, int quoteLength) {
    }

    /**
     * The length of {@code text} in runes.
     *
     * <p>Unlike {@link String#length()}, an astral character counts once: {@code runeLength("🚀")} is
     * 1 where {@code "🚀".length()} is 2.
     */
    public static int runeLength(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }

    /**
     * Translates a Java (UTF-16) index into a rune offset.
     *
     * <p>Out-of-range indices are clamped, and an index landing on the trailing half of a surrogate
     * pair resolves to the rune that pair forms rather than the one after it.
     */
    public static int toRuneOffset(String text, int charOffset) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        var end = Math.min(Math.max(charOffset, 0), text.length());

        var runes = 0;
        for (var i = 0; i < end; i++) {
            // Every code unit starts a rune except the trailing half of a surrogate pair.
            if (!Character.isLowSurrogate(text.charAt(i))) {
                runes++;
            }
        }

        // Landing inside a pair belongs to the rune that pair forms, so undo the lead half counted
        // above rather than reporting the rune after it.
        if (end < text.length() && Character.isLowSurrogate(text.charAt(end))) {
            runes--;
        }
        return runes;
    }

    /**
     * Translates a rune offset into a Java (UTF-16) string index — the inverse of
     * {@link #toRuneOffset}. Offsets past the end of {@code text} resolve to its length.
     */
    public static int toCharOffset(String text, int runeOffset) {
        if (text == null || runeOffset <= 0) {
            return 0;
        }
        var i = 0;
        for (var rune = 0; rune < runeOffset && i < text.length(); rune++) {
            // Astral characters occupy two code units; everything else occupies one.
            i += Character.charCount(text.codePointAt(i));
        }
        return Math.min(i, text.length());
    }

    /** Slices {@code text} from a rune offset to its end. */
    public static String slice(String text, int start) {
        return text == null ? "" : text.substring(toCharOffset(text, start));
    }

    /**
     * Slices {@code text} using rune offsets — the way to pull a quote out of a message:
     * {@code Runes.slice(replyTo.text(), replyTo.quoteStart(), length)}.
     */
    public static String slice(String text, int start, int length) {
        if (text == null) {
            return "";
        }
        var from = toCharOffset(text, start);
        var to = toCharOffset(text, start + Math.max(length, 0));
        return text.substring(from, Math.max(from, to));
    }

    /** Finds {@code quote} in {@code text} and returns its rune offsets. See {@link #quoteRange(String, String, int)}. */
    public static QuoteRange quoteRange(String text, String quote) {
        return quoteRange(text, quote, 0);
    }

    /**
     * Finds {@code quote} inside {@code text} and returns rune offsets safe to put on the wire,
     * bypassing the UTF-16 drift a raw {@link String#indexOf} would introduce.
     *
     * <pre>{@code
     * var range = Runes.quoteRange(trigger.replyTo().text(), "the interesting part");
     * }</pre>
     *
     * @param from a rune offset to start searching from, to skip earlier occurrences
     * @return the range, or {@code null} when {@code quote} is empty or does not occur
     */
    public static QuoteRange quoteRange(String text, String quote, int from) {
        if (text == null || quote == null || quote.isEmpty()) {
            return null;
        }

        var at = text.indexOf(quote, toCharOffset(text, from));

        // A match may begin on the trailing half of a surrogate pair, which is half of a character
        // rather than a real occurrence. Keep looking from the next code unit.
        while (at >= 0 && Character.isLowSurrogate(text.charAt(at))) {
            at = text.indexOf(quote, at + 1);
        }

        return at < 0 ? null : new QuoteRange(toRuneOffset(text, at), runeLength(quote));
    }
}
