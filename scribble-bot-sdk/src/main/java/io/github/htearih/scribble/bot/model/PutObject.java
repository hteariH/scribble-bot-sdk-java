package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.github.htearih.scribble.bot.json.UnsignedRgba;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * Draws a {@value ScratchpadObjectMessage#LINE_FLOATS} object on top of a frame, or replaces the
 * bot's own object with the same {@link #localId()}.
 *
 * <p>The drawing fields are the same as in {@link ScratchpadObjectMessage}: see its
 * {@link ScratchpadObjectMessage#rgba() rgba}, {@link ScratchpadObjectMessage#lineWidth() lineWidth}
 * and {@link ScratchpadObjectMessage#points() points} for how they render. Upstream will change the
 * format objects are <em>read</em> in before 1.0, but this is the format bots draw in, and it stays.
 *
 * <p>Name the frame by exactly one of {@link #frameId()} or {@link #frameLocalId()}. If it is not
 * there — its layer was removed, say — nothing is drawn and the rest of the request still applies.
 *
 * <p>Filled polygons ({@code lineWidth} 0) and lines wider than 2.5 need the fill tool permission in
 * the room, as they do for people; without it the whole request fails with a 403.
 *
 * @param localId      the bot's own ID for this object — see {@link #localId()}
 * @param frameId      the global ID of the frame to draw on, e.g.
 *                     {@link DefaultLayers#FOREGROUND_FRAME_ID} or one from
 *                     {@link ScratchpadLayerMessage#frames()}, or {@code null}
 * @param frameLocalId one of the bot's own {@link PutLayer#frameLocalIds()}, or {@code null}
 * @param rgba         the colour packed as {@code R << 24 | G << 16 | B << 8 | A}, sent as the
 *                     unsigned 32-bit number the platform expects
 * @param lineWidth    from 0 to 100; 0 is a filled polygon, not a hairline
 * @param points       from 1 to 2000 {@code [x, y]} pairs of finite numbers, flat
 */
@JsonPropertyOrder({"type", "localId", "frameId", "frameLocalId", "objectType", "rgba", "lineWidth", "points"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PutObject(
        long localId,
        Long frameId,
        Long frameLocalId,
        @JsonSerialize(using = UnsignedRgba.Serializer.class)
        @JsonDeserialize(using = UnsignedRgba.Deserializer.class)
        int rgba,
        double lineWidth,
        List<Double> points)
        implements Action {

    /** The wire discriminator. */
    public static final String TYPE = "sp.putObject";

    /** The widest line the platform draws. */
    public static final double MAX_LINE_WIDTH = 100;

    /** The most {@code [x, y]} pairs one object may carry. */
    public static final int MAX_POINTS = 2000;

    public PutObject {
        // Kept nullable so validation can name it; copied so the payload cannot change after it.
        points = points == null ? null : Collections.unmodifiableList(new ArrayList<>(points));
    }

    /** Draws on the frame with the global ID {@code frameId}. */
    public static PutObject onFrame(long localId, long frameId, int rgba, double lineWidth, List<Double> points) {
        return new PutObject(localId, frameId, null, rgba, lineWidth, points);
    }

    /** Draws on the bot's own frame {@code frameLocalId}; the layer need not be named. */
    public static PutObject onOwnFrame(
            long localId, long frameLocalId, int rgba, double lineWidth, List<Double> points) {
        return new PutObject(localId, null, frameLocalId, rgba, lineWidth, points);
    }

    @Override
    @JsonProperty("type")
    public String type() {
        return TYPE;
    }

    /** Always {@value ScratchpadObjectMessage#LINE_FLOATS}: the only format bots draw in. */
    @JsonProperty("objectType")
    public String objectType() {
        return ScratchpadObjectMessage.LINE_FLOATS;
    }

    /**
     * The bot's own ID for this object, unique among its objects in the room's drawing session; from
     * 1 to {@link Action#MAX_LOCAL_ID}.
     *
     * <p>Another object under the same ID replaces the old one in place, under the same
     * {@code objectId}; onto another frame, it is drawn there as a new object. A new object goes on
     * top of its frame, so objects stack in the order they are sent. Once the object is removed, by
     * the bot or anybody else, the ID draws a new one.
     */
    @Override
    public long localId() {
        return localId;
    }
}
