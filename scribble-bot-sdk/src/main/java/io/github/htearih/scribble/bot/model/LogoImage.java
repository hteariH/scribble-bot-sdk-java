package io.github.htearih.scribble.bot.model;

/**
 * The site logo PNG together with the validator to ask for it again.
 *
 * <p>The logo arrives masked to the letter shapes, so everything around them is transparent,
 * ready to be drawn over whatever your bot is drawing. Take its dimensions from the image itself
 * and don't hardcode them since the logo can be resized in the future.
 *
 * <p>Keep the returned {@code etag} and pass it back as {@code ifNoneMatch} on the next call to
 * skip re-downloading the logo if nobody has drawn there since.
 */
public record LogoImage(byte[] image, String etag) {
}
