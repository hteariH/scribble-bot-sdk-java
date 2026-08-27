package io.github.htearih.scribble.bot;

import java.util.List;

/**
 * A payload this SDK refused to send.
 *
 * <p>Thrown before anything goes over the wire: the platform would reject these too, but finding out
 * locally costs no round trip and points at the offending field instead of returning a flat 400.
 */
public class ScribblePubValidationError extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final transient List<ValidationError> errors;

    public ScribblePubValidationError(String message, List<ValidationError> errors) {
        super(message);
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** Every problem found, not just the one named in {@link #getMessage()}. */
    public List<ValidationError> getErrors() {
        return errors;
    }
}
