package io.github.htearih.scribble.bot.model;

/**
 * A room preview PNG together with the validator to ask for it again.
 *
 * <p>Keep the returned {@code lastModified} and pass it back as {@code ifModifiedSince} on the
 * next call to skip re-downloading a preview if nobody has changed it since.
 */
public record RoomPreviewImage(byte[] image, String lastModified) {
}
