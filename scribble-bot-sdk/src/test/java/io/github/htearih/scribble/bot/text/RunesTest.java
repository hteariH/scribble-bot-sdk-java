package io.github.htearih.scribble.bot.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The wire counts quote offsets in runes; Java counts {@code char}s. Every case here is one an
 * {@code indexOf}/{@code substring} implementation would get wrong.
 */
class RunesTest {

    /** 'a', a rocket (one rune, two chars), 'b' — so char length 4 against rune length 3. */
    private static final String ASTRAL = "a🚀b";

    @Test
    void countsAstralCharactersOnce() {
        assertThat(ASTRAL.length()).isEqualTo(4);
        assertThat(Runes.runeLength(ASTRAL)).isEqualTo(3);
        assertThat(Runes.runeLength("plain")).isEqualTo(5);
        assertThat(Runes.runeLength("")).isZero();
        assertThat(Runes.runeLength(null)).isZero();
    }

    @Test
    void convertsCharOffsetsToRuneOffsets() {
        assertThat(Runes.toRuneOffset(ASTRAL, 0)).isZero();
        assertThat(Runes.toRuneOffset(ASTRAL, 1)).isEqualTo(1);
        assertThat(Runes.toRuneOffset(ASTRAL, 3)).isEqualTo(2);
        assertThat(Runes.toRuneOffset(ASTRAL, 4)).isEqualTo(3);
    }

    @Test
    void resolvesAnOffsetLandingInsideASurrogatePairToThatRune() {
        // Char 2 is the trailing half of the rocket. It belongs to the rocket (rune 1), not to 'b'.
        assertThat(Runes.toRuneOffset(ASTRAL, 2)).isEqualTo(1);
    }

    @Test
    void clampsOutOfRangeOffsets() {
        assertThat(Runes.toRuneOffset(ASTRAL, -5)).isZero();
        assertThat(Runes.toRuneOffset(ASTRAL, 99)).isEqualTo(3);
        assertThat(Runes.toCharOffset(ASTRAL, -5)).isZero();
        assertThat(Runes.toCharOffset(ASTRAL, 99)).isEqualTo(4);
    }

    @Test
    void convertsRuneOffsetsBackToCharOffsets() {
        assertThat(Runes.toCharOffset(ASTRAL, 0)).isZero();
        assertThat(Runes.toCharOffset(ASTRAL, 1)).isEqualTo(1);
        assertThat(Runes.toCharOffset(ASTRAL, 2)).isEqualTo(3);
        assertThat(Runes.toCharOffset(ASTRAL, 3)).isEqualTo(4);
    }

    @Test
    void roundTripsEveryRuneBoundary() {
        for (var rune = 0; rune <= Runes.runeLength(ASTRAL); rune++) {
            assertThat(Runes.toRuneOffset(ASTRAL, Runes.toCharOffset(ASTRAL, rune)))
                    .as("rune %d", rune)
                    .isEqualTo(rune);
        }
    }

    @Test
    void slicesByRunesWithoutSplittingAPair() {
        assertThat(Runes.slice(ASTRAL, 1, 1)).isEqualTo("🚀");
        assertThat(Runes.slice(ASTRAL, 0, 2)).isEqualTo("a🚀");
        assertThat(Runes.slice(ASTRAL, 1)).isEqualTo("🚀b");
        assertThat(Runes.slice(ASTRAL, 2, 99)).isEqualTo("b");
        assertThat(Runes.slice(ASTRAL, 1, -3)).isEmpty();
        assertThat(Runes.slice(null, 0, 1)).isEmpty();
    }

    @Test
    void findsAQuoteAtItsRuneOffsetRatherThanItsCharOffset() {
        var text = ASTRAL + " cat";

        var range = Runes.quoteRange(text, "cat");

        // indexOf would say 5 — one too far right, because the rocket costs two chars.
        assertThat(text.indexOf("cat")).isEqualTo(5);
        assertThat(range.quoteStart()).isEqualTo(4);
        assertThat(range.quoteLength()).isEqualTo(3);
        // And the offsets read back the fragment they were derived from.
        assertThat(Runes.slice(text, range.quoteStart(), range.quoteLength())).isEqualTo("cat");
    }

    @Test
    void countsAQuotesOwnAstralCharactersOnce() {
        var text = "look: 🚀🚀 there";

        var range = Runes.quoteRange(text, "🚀🚀");

        assertThat(range.quoteStart()).isEqualTo(6);
        assertThat(range.quoteLength()).isEqualTo(2);
    }

    @Test
    void skipsEarlierOccurrencesWhenAskedTo() {
        var text = "cat and cat";

        assertThat(Runes.quoteRange(text, "cat").quoteStart()).isZero();
        assertThat(Runes.quoteRange(text, "cat", 1).quoteStart()).isEqualTo(8);
    }

    @Test
    void returnsNothingForAQuoteItCannotPlace() {
        assertThat(Runes.quoteRange("hello", "goodbye")).isNull();
        assertThat(Runes.quoteRange("hello", "")).isNull();
        assertThat(Runes.quoteRange("hello", null)).isNull();
        assertThat(Runes.quoteRange(null, "hello")).isNull();
    }
}
