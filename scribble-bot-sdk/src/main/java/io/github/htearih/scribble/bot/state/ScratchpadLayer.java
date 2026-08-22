package io.github.htearih.scribble.bot.state;

import java.util.List;

/**
 * A layer in the state.
 *
 * <p>The frames are also declared in layers: a frame exists because a layer has its ID in its
 * {@code frames} array and is dropped as soon as it's no longer there.
 */
public record ScratchpadLayer(int layerId, List<Integer> frames, int lastEventId) {
}
