package io.github.htearih.scribble.bot.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flattens markup into plain text for a scribble.pub room, which renders none of it.
 *
 * <p>Useful mostly for LLM-backed bots: you ask the model for plain text and it hands you
 * {@code **bold**} anyway (observed reaching a live room), so this is the safety net rather than
 * the primary defence. Links keep their target ({@code <a href="u">t</a>} becomes {@code t (u)}),
 * since dropping it would lose the only useful part of a cited source.
 *
 * <p>Deliberately conservative with Markdown: only the unambiguous {@code **}, {@code __},
 * backticks and heading hashes are removed. Single {@code *}/{@code _} emphasis is left alone —
 * stripping it would corrupt ordinary text like {@code some_var_name} or {@code 3 * 4}.
 */
public final class PlainText {

    private static final Pattern ANCHOR =
            Pattern.compile("<a\\s+[^>]*href\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>(.*?)</a>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern LINE_BREAK = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARAGRAPH = Pattern.compile("</p\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern EXCESS_BLANK_LINES = Pattern.compile("\n{3,}");

    private static final Pattern CODE_FENCE =
            Pattern.compile("```[a-zA-Z0-9+#-]*\\n?(.*?)```", Pattern.DOTALL);
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`\n]+)`");
    private static final Pattern BOLD_STARS =
            Pattern.compile("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*", Pattern.DOTALL);
    private static final Pattern BOLD_UNDERSCORES =
            Pattern.compile("__(?=\\S)(.+?)(?<=\\S)__", Pattern.DOTALL);
    private static final Pattern HEADING = Pattern.compile("(?m)^\\s{0,3}#{1,6}\\s+");

    private PlainText() {
    }

    /** HTML and unambiguous Markdown out, readable text in. Never returns {@code null}. */
    public static String flatten(String markup) {
        if (markup == null || markup.isBlank()) {
            return "";
        }
        var text = ANCHOR.matcher(markup).replaceAll(match -> {
            var url = match.group(1);
            var label = TAG.matcher(match.group(2)).replaceAll("").trim();
            if (label.isEmpty() || label.equals(url)) {
                return Matcher.quoteReplacement(url);
            }
            return Matcher.quoteReplacement(label + " (" + url + ")");
        });
        text = LINE_BREAK.matcher(text).replaceAll("\n");
        text = PARAGRAPH.matcher(text).replaceAll("\n");
        text = TAG.matcher(text).replaceAll("");
        text = unescape(text);
        text = stripMarkdown(text);
        return EXCESS_BLANK_LINES.matcher(text).replaceAll("\n\n").trim();
    }

    /** Truncates on a word boundary when possible, marking that something was cut. */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        var ellipsis = " […]";
        var room = Math.max(0, maxLength - ellipsis.length());
        var cut = text.lastIndexOf(' ', room);
        return text.substring(0, cut > room / 2 ? cut : room).stripTrailing() + ellipsis;
    }

    private static String stripMarkdown(String text) {
        var out = CODE_FENCE.matcher(text).replaceAll(match -> Matcher.quoteReplacement(match.group(1).strip()));
        out = INLINE_CODE.matcher(out).replaceAll(match -> Matcher.quoteReplacement(match.group(1)));
        out = BOLD_STARS.matcher(out).replaceAll(match -> Matcher.quoteReplacement(match.group(1)));
        out = BOLD_UNDERSCORES.matcher(out).replaceAll(match -> Matcher.quoteReplacement(match.group(1)));
        return HEADING.matcher(out).replaceAll("");
    }

    private static String unescape(String text) {
        return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                // last: an escaped ampersand must not resurrect the entities above
                .replace("&amp;", "&");
    }
}
