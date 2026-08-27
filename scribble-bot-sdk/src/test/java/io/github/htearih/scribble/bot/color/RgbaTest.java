package io.github.htearih.scribble.bot.color;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RgbaTest {

    @Test
    void formatsAsCssHexWithAlphaAlwaysPresent() {
        assertThat(Rgba.toHex(0xdbffb9ff)).isEqualTo("#dbffb9ff");
        assertThat(Rgba.toHex(0x000000ff)).isEqualTo("#000000ff");
        assertThat(Rgba.toHex(0x00000000)).isEqualTo("#00000000");
    }

    @Test
    void splitsChannelsWithAlphaAsTheLowestByte() {
        // The trap: 0xff0000ff is opaque red, not a transparent-ish blue.
        var red = Rgba.toComponents(0xff0000ff);

        assertThat(red.r()).isEqualTo(255);
        assertThat(red.g()).isZero();
        assertThat(red.b()).isZero();
        assertThat(red.a()).isEqualTo(255);
    }

    @Test
    void readsAHalfTransparentColourCorrectly() {
        var components = Rgba.toComponents(0x1020307f);

        assertThat(components.r()).isEqualTo(0x10);
        assertThat(components.g()).isEqualTo(0x20);
        assertThat(components.b()).isEqualTo(0x30);
        assertThat(components.a()).isEqualTo(0x7f);
    }

    @Test
    void reordersIntoTheArgbJavaImagingExpects() {
        assertThat(Rgba.toArgb(0xff0000ff)).isEqualTo(0xffff0000);
        assertThat(Rgba.toComponents(0x1020307f).toArgb()).isEqualTo(0x7f102030);
    }

    @Test
    void roundTripsThroughComponents() {
        for (var packed : new int[] {0x00000000, 0xffffffff, 0xdbffb9ff, 0x1020307f}) {
            var c = Rgba.toComponents(packed);
            var repacked = (c.r() << 24) | (c.g() << 16) | (c.b() << 8) | c.a();
            assertThat(repacked).isEqualTo(packed);
        }
    }
}
