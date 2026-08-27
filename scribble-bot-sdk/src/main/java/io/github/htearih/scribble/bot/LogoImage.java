package io.github.htearih.scribble.bot;

/**
 * The site logo PNG, with the validator needed to ask for it again cheaply.
 *
 * @param image the PNG bytes, masked to the letter shapes so everything around them is transparent.
 *              Take the dimensions from the image itself rather than hardcoding them — the logo can
 *              be resized
 * @param etag  the {@code ETag} of this logo, to pass straight back to
 *              {@link ScribblePubBot#getLogoImage(ScribblePubBot.LogoTheme, String)} next time so an
 *              undrawn-on logo costs a 304 instead of a download
 */
public record LogoImage(byte[] image, String etag) {
}
