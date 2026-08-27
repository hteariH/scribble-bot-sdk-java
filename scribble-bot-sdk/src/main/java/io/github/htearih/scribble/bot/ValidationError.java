package io.github.htearih.scribble.bot;

/**
 * One thing wrong with a payload, located by a dot-separated path.
 *
 * @param path    where the problem is, e.g. {@code actions.0.replyTo.messageId}
 * @param message what is wrong with it
 */
public record ValidationError(String path, String message) {

    @Override
    public String toString() {
        return path == null || path.isBlank() ? message : path + " " + message;
    }
}
