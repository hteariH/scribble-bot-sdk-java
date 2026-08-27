package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * A drawn object on a frame.
 *
 * <p>These arrive in ascending {@link #objectId()} order, which is both the order they were drawn in
 * and the order they render in, so a bot can append them to its local state and draw them straight
 * away without sorting. Expect gaps: erased objects keep their IDs but are never delivered. If the
 * frame is unknown, drop the object rather than inventing one — not expected, but defined.
 *
 * <p>The payload is flat: {@link #objectType()} says how to read the fields beside it. Only
 * {@value #LINE_FLOATS} exists today, and it is a convenience the server converts into rather than
 * its native wire form — upstream warns that before 1.0 these will start arriving under a different
 * type with a denser encoding. Reading {@code objectType} rather than assuming it is what keeps a
 * bot alive across that change, which is why this record models the union flatly instead of
 * committing to one payload shape.
 *
 * @param type       always {@value #TYPE}
 * @param objectId   a session-scoped identifier for the object. Layers, frames and objects each have
 *                   their own independent ID counters
 * @param frameId    the frame this object belongs to
 * @param eventId    the ID of the event that last modified this object
 * @param objectType how to read the payload; {@value #LINE_FLOATS} today
 * @param rgba       the colour packed as {@code R << 24 | G << 16 | B << 8 | A} — see {@link #rgba()}
 * @param lineWidth  the stroke width, or {@code 0} for a filled polygon — see {@link #lineWidth()}
 * @param points     a flat array of coordinates {@code [x1, y1, x2, y2, …]} — see {@link #points()}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScratchpadObjectMessage(
        String type,
        long objectId,
        long frameId,
        long eventId,
        String objectType,
        Integer rgba,
        Double lineWidth,
        List<Double> points)
        implements RoomMessage {

    /** The wire discriminator for this message. */
    public static final String TYPE = "sp.object";

    /** The only object payload the platform sends today: a simple polyline of float coordinates. */
    public static final String LINE_FLOATS = "line.floats";

    public ScratchpadObjectMessage {
        type = type == null ? TYPE : type;
        points = points == null ? List.of() : List.copyOf(points);
    }

    /** Whether this object carries a {@value #LINE_FLOATS} payload, i.e. the fields below mean anything. */
    public boolean isLineFloats() {
        return LINE_FLOATS.equals(objectType);
    }

    /**
     * The colour packed as {@code R << 24 | G << 16 | B << 8 | A}, the same byte order as the CSS
     * {@code #rrggbbaa} notation — so alpha is the <em>lowest</em> byte, not the highest, and
     * {@code 0xff0000ff} is opaque red.
     *
     * <p>Use {@link io.github.htearih.scribble.bot.color.Rgba#toHex(int)} for a ready-to-draw CSS
     * string, or {@link io.github.htearih.scribble.bot.color.Rgba#toComponents(int)} for the
     * channels. {@code null} when the payload is not {@value #LINE_FLOATS}.
     */
    @Override
    public Integer rgba() {
        return rgba;
    }

    /**
     * The width of the line.
     *
     * <p><strong>Zero is not a hairline.</strong> A width of {@code 0} means the points describe a
     * filled polygon, implicitly closed from the last point back to the first. Anything else is a
     * stroked line with round caps and round joins.
     *
     * <p>{@code null} when the payload is not {@value #LINE_FLOATS}.
     */
    @Override
    public Double lineWidth() {
        return lineWidth;
    }

    /**
     * A flat array of coordinates {@code [x1, y1, x2, y2, …]} in the canvas space given by
     * {@link ScratchpadSessionMetaMessage#canvasWidth()}.
     *
     * <p>Fewer than four coordinates means a single point: draw a filled circle of radius
     * {@code lineWidth / 2} around {@code [x1, y1]}. Worth handling explicitly, because many drawing
     * APIs skip a {@code moveTo}/{@code lineTo} pair on identical points even with round caps set,
     * and the dot then silently vanishes.
     */
    @Override
    public List<Double> points() {
        return points;
    }

    /** Whether this object is the single-point case described in {@link #points()}. */
    public boolean isDot() {
        return points.size() < 4;
    }
}
