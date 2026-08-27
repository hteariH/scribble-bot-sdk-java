package io.github.htearih.scribble.bot.color;

/**
 * The packed colours scribble.pub draws with, as carried by
 * {@link io.github.htearih.scribble.bot.model.ScratchpadObjectMessage#rgba()}.
 *
 * <p>Colours are packed as {@code R << 24 | G << 16 | B << 8 | A} — byte for byte the CSS
 * {@code #rrggbbaa} order, which is why {@link #toHex(int)} reinterprets the number rather than
 * converting it. <strong>Alpha is the lowest byte, not the highest</strong>, so {@code 0xff0000ff}
 * is opaque red and not transparent-ish blue; that is the one thing worth double-checking when
 * feeding these into an AWT or JavaFX colour, which both put alpha first.
 */
public final class Rgba {

    private Rgba() {
    }

    /**
     * The channels of a packed colour, each 0-255 exactly as they appear on the wire.
     *
     * @param r red
     * @param g green
     * @param b blue
     * @param a opacity, 0-255 — divide by 255 for the 0-1 alpha most drawing APIs expect
     */
    public record Components(int r, int g, int b, int a) {

        /** This colour as {@link java.awt.Color} would order it: {@code A << 24 | R << 16 | G << 8 | B}. */
        public int toArgb() {
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    /**
     * Formats a packed colour as CSS hex: {@code 0xdbffb9ff} becomes {@code "#dbffb9ff"}. Alpha is
     * always included, even when opaque.
     */
    public static String toHex(int rgba) {
        return "#" + String.format("%08x", rgba);
    }

    /** Splits a packed colour into its channels, for pixel maths or custom compositing. */
    public static Components toComponents(int rgba) {
        return new Components((rgba >>> 24) & 0xff, (rgba >>> 16) & 0xff, (rgba >>> 8) & 0xff, rgba & 0xff);
    }

    /** The ARGB integer {@link java.awt.Color#Color(int, boolean)} and {@code BufferedImage} expect. */
    public static int toArgb(int rgba) {
        return toComponents(rgba).toArgb();
    }
}
