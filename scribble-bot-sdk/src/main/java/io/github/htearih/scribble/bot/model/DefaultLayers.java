package io.github.htearih.scribble.bot.model;

/**
 * The fixed IDs of the two layers every drawing session starts with, and of their single frames.
 *
 * <p>People can neither change nor remove the default layers, so these IDs never move. Draw on the
 * foreground with {@code PutObject.onFrame(localId, DefaultLayers.FOREGROUND_FRAME_ID, …)}, or put a
 * layer of your own right under it with {@code LayerPosition.below(DefaultLayers.FOREGROUND_LAYER_ID)}.
 */
public final class DefaultLayers {

    /** The default foreground layer, {@code fg} in the UI. */
    public static final long FOREGROUND_LAYER_ID = 0;

    /** The default background layer, {@code bg} in the UI, right under the foreground in a new session. */
    public static final long BACKGROUND_LAYER_ID = 1;

    /** The only frame of {@link #FOREGROUND_LAYER_ID}. */
    public static final long FOREGROUND_FRAME_ID = 0;

    /** The only frame of {@link #BACKGROUND_LAYER_ID}. */
    public static final long BACKGROUND_FRAME_ID = 1;

    private DefaultLayers() {
    }
}
