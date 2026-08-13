package io.github.htearih.scribble.bot.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MentionsTest {

    @Test
    void stripsTheHandleAnywhereInTheTextAndAnyCase() {
        assertThat(Mentions.stripHandle("@Mary, привет", "mary")).isEqualTo("привет");
        assertThat(Mentions.stripHandle("hey @mary can you draw?", "mary")).isEqualTo("hey can you draw?");
        assertThat(Mentions.stripHandle("@mary: hello", "mary")).isEqualTo("hello");
    }

    @Test
    void leavesLongerWordsThatMerelyStartWithTheHandle() {
        assertThat(Mentions.stripHandle("@maryanne hello", "mary")).isEqualTo("@maryanne hello");
    }

    @Test
    void handlesNullTextAndAbsentHandles() {
        assertThat(Mentions.stripHandle(null, "mary")).isEmpty();
        assertThat(Mentions.stripHandle("  @mary hi  ", (String) null)).isEqualTo("@mary hi");
        assertThat(Mentions.stripHandle("  @mary hi  ", "")).isEqualTo("@mary hi");
        assertThat(Mentions.strip("@mary hi", Mentions.pattern("mary"))).isEqualTo("hi");
    }

    @Test
    void aBareMentionLeavesNothingBehind() {
        assertThat(Mentions.stripHandle("@mary", "mary")).isEmpty();
    }
}
