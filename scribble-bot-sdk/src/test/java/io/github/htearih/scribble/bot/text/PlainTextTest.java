package io.github.htearih.scribble.bot.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlainTextTest {

    @Test
    void stripsFormattingTags() {
        assertThat(PlainText.flatten("<b>Hi</b> <i>there</i>, <s>old</s> <u>news</u>"))
                .isEqualTo("Hi there, old news");
    }

    @Test
    void keepsLinkTargetsAlongsideTheirLabel() {
        assertThat(PlainText.flatten("see <a href=\"https://x.dev\">the docs</a>"))
                .isEqualTo("see the docs (https://x.dev)");
    }

    @Test
    void collapsesALinkWhoseLabelIsTheUrl() {
        assertThat(PlainText.flatten("<a href=\"https://x.dev\">https://x.dev</a>"))
                .isEqualTo("https://x.dev");
    }

    @Test
    void unescapesEntitiesWithoutResurrectingMarkup() {
        assertThat(PlainText.flatten("2 &lt; 3 &amp;&amp; a &amp;lt; b")).isEqualTo("2 < 3 && a &lt; b");
    }

    @Test
    void turnsBreaksIntoNewlinesAndTrimsRuns() {
        assertThat(PlainText.flatten("a<br>b<br/><br/><br/>c")).isEqualTo("a\nb\n\nc");
    }

    @Test
    void handlesNullAndBlank() {
        assertThat(PlainText.flatten(null)).isEmpty();
        assertThat(PlainText.flatten("   ")).isEmpty();
    }

    @Test
    void stripsMarkdownTheModelSlippedIn() {
        // observed reaching a live room despite the persona forbidding it
        assertThat(PlainText.flatten("**Your name is aberk.** So?")).isEqualTo("Your name is aberk. So?");
        assertThat(PlainText.flatten("__also bold__ and `code`")).isEqualTo("also bold and code");
        assertThat(PlainText.flatten("## Heading\ntext")).isEqualTo("Heading\ntext");
        assertThat(PlainText.flatten("```java\nint x = 1;\n```")).isEqualTo("int x = 1;");
    }

    @Test
    void leavesAmbiguousPunctuationAlone() {
        // single * and _ are not stripped: they occur in ordinary text far more often than as
        // emphasis, and mangling identifiers or arithmetic would be worse than a stray asterisk
        assertThat(PlainText.flatten("some_var_name and 3 * 4 = 12"))
                .isEqualTo("some_var_name and 3 * 4 = 12");
    }

    @Test
    void truncateLeavesShortTextAlone() {
        assertThat(PlainText.truncate("short", 100)).isEqualTo("short");
    }

    @Test
    void truncateCutsOnAWordBoundaryAndMarksIt() {
        var truncated = PlainText.truncate("the quick brown fox jumps over the lazy dog", 20);

        assertThat(truncated).hasSizeLessThanOrEqualTo(20).endsWith(" […]").doesNotContain("jum ");
    }
}
