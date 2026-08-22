package io.github.htearih.scribble.bot.state;

import io.github.htearih.scribble.bot.model.ScratchpadObject;
import java.util.List;

/**
 * A frame in the state. A child node of a layer.
 */
public record ScratchpadFrame(int frameId, int layerId, List<ScratchpadObject> objects) {
}
