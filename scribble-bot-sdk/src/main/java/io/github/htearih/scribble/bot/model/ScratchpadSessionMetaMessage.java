package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Global metadata about the current drawing session in a room.
 *
 * <p>Receiving this <strong>begins a new drawing</strong>, usually because the room was cleared. A
 * bot holding local state must reset it — layers, frames, objects, last event ID — back to empty;
 * {@link io.github.htearih.scribble.bot.state.ScratchpadState} does exactly that. The default layers
 * arrive separately, as ordinary {@link ScratchpadLayerMessage}s.
 *
 * @param type           always {@value #TYPE}
 * @param currentSession an identifier for the drawing itself. An old state restored into the same
 *                       or another room keeps the identifier it started under, though that is
 *                       exceptional today
 * @param eventId        the ID of the event that created this metadata, sharing the counter with
 *                       every other {@code eventId}/{@code lastEventId}
 * @param seqId          the session's sequential ID in a room with its gallery enabled, as used by
 *                       permalinks like {@code https://scribble.pub/main/167}
 * @param canvasWidth    the width of the room canvas; every object coordinate is in this space
 * @param canvasHeight   the height of the room canvas
 * @param animationTickMs how long each animation frame lasts, in milliseconds — see
 *                       {@link #animationTickMs()}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScratchpadSessionMetaMessage(
        String type,
        long currentSession,
        long eventId,
        long seqId,
        int canvasWidth,
        int canvasHeight,
        Integer animationTickMs)
        implements RoomMessage {

    /** The wire discriminator for this message. */
    public static final String TYPE = "sp.sessionMeta";

    public ScratchpadSessionMetaMessage {
        type = type == null ? TYPE : type;
    }

    /** Metadata without an {@link #animationTickMs()}, as servers before upstream 0.5.0 sent it. */
    public ScratchpadSessionMetaMessage(
            String type, long currentSession, long eventId, long seqId, int canvasWidth, int canvasHeight) {
        this(type, currentSession, eventId, seqId, canvasWidth, canvasHeight, null);
    }

    /**
     * How long each frame of the drawing's animations lasts, in milliseconds: 125 (8 frames a
     * second) now, but 250 for older drawings. It may change again, so read it rather than hardcode
     * it. {@code null} only from a server older than upstream 0.5.0.
     */
    @Override
    public Integer animationTickMs() {
        return animationTickMs;
    }
}
