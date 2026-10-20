package io.github.htearih.scribble.bot.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.htearih.scribble.bot.model.AnimationMode;
import io.github.htearih.scribble.bot.model.RoomMessage;
import io.github.htearih.scribble.bot.model.ScratchpadLastEventIdMessage;
import io.github.htearih.scribble.bot.model.ScratchpadLayerMessage;
import io.github.htearih.scribble.bot.model.ScratchpadObjectMessage;
import io.github.htearih.scribble.bot.model.ScratchpadRollMessage;
import io.github.htearih.scribble.bot.model.ScratchpadSessionMetaMessage;
import io.github.htearih.scribble.bot.model.ScratchpadSetLayerOrderMessage;
import io.github.htearih.scribble.bot.model.UnknownRoomMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScratchpadStateTest {

    @Test
    void buildsLayersFramesAndObjectsFromASnapshot() {
        var state = ScratchpadState.fromMessages(List.of(
                meta(1, 10),
                layer(100, List.of(1000L), 11),
                layer(200, List.of(2000L), 12),
                order(List.of(200L, 100L), -1),
                object(1, 1000, 13),
                object(2, 1000, 14),
                new ScratchpadLastEventIdMessage(null, 99)));

        assertThat(state.sessionMeta().canvasWidth()).isEqualTo(1000);
        assertThat(state.layers()).containsOnlyKeys(100L, 200L);
        assertThat(state.layerOrder()).containsExactly(200L, 100L);
        assertThat(state.frames()).containsOnlyKeys(1000L, 2000L);
        assertThat(state.frames().get(1000L).layerId()).isEqualTo(100L);
        assertThat(state.objects()).containsOnlyKeys(1L, 2L);
        assertThat(state.lastEventId()).isEqualTo(99);
    }

    @Test
    void keepsObjectsInArrivalOrderWhichIsRenderOrder() {
        var state = ScratchpadState.fromMessages(
                List.of(layer(100, List.of(1000L), 1), object(7, 1000, 2), object(9, 1000, 3), object(11, 1000, 4)));

        assertThat(state.frames().get(1000L).objects())
                .extracting(ScratchpadObjectMessage::objectId)
                .containsExactly(7L, 9L, 11L);
    }

    @Test
    void replacesAnObjectInPlaceRatherThanAppendingItTwice() {
        var state = ScratchpadState.fromMessages(
                List.of(layer(100, List.of(1000L), 1), object(7, 1000, 2), object(9, 1000, 3)));

        state.applyMessage(object(7, 1000, 50));

        assertThat(state.frames().get(1000L).objects())
                .extracting(ScratchpadObjectMessage::objectId)
                .containsExactly(7L, 9L);
        assertThat(state.objects().get(7L).eventId()).isEqualTo(50);
    }

    @Test
    void dropsAnObjectWhoseFrameIsUnknown() {
        var state = ScratchpadState.fromMessages(List.of(object(1, 4242, 5)));

        assertThat(state.objects()).isEmpty();
        // And the orphan does not advance the cursor either.
        assertThat(state.lastEventId()).isEqualTo(-1);
    }

    @Test
    void anEmptyFrameListDeletesTheLayerAndEverythingUnderIt() {
        var state = ScratchpadState.fromMessages(List.of(
                layer(100, List.of(1000L), 1),
                layer(200, List.of(2000L), 2),
                order(List.of(100L, 200L), -1),
                object(1, 1000, 3),
                object(2, 2000, 4)));

        state.applyMessage(layer(100, List.of(), 5));

        assertThat(state.layers()).containsOnlyKeys(200L);
        assertThat(state.layerOrder()).containsExactly(200L);
        assertThat(state.frames()).containsOnlyKeys(2000L);
        assertThat(state.objects()).containsOnlyKeys(2L);
    }

    @Test
    void dropsFramesALayerUpdateTookAway() {
        var state = ScratchpadState.fromMessages(List.of(
                layer(100, List.of(1000L, 1001L), 1), object(1, 1000, 2), object(2, 1001, 3)));

        state.applyMessage(layer(100, List.of(1001L), 4));

        assertThat(state.frames()).containsOnlyKeys(1001L);
        assertThat(state.objects()).containsOnlyKeys(2L);
        assertThat(state.layers().get(100L).frames()).containsExactly(1001L);
    }

    @Test
    void sessionMetaClearsEverythingBecauseItBeginsANewDrawing() {
        var state = ScratchpadState.fromMessages(
                List.of(layer(100, List.of(1000L), 1), object(1, 1000, 2), order(List.of(100L), -1)));

        state.applyMessage(meta(2, 500));

        assertThat(state.layers()).isEmpty();
        assertThat(state.frames()).isEmpty();
        assertThat(state.objects()).isEmpty();
        assertThat(state.layerOrder()).isEmpty();
        assertThat(state.sessionMeta().currentSession()).isEqualTo(2);
        assertThat(state.lastEventId()).isEqualTo(500);
    }

    @Test
    void tracksTheHighestEventIdRatherThanTheLatest() {
        // Event IDs carry no ordering guarantee, and a snapshot's layer order arrives as -1.
        var state = ScratchpadState.fromMessages(
                List.of(layer(100, List.of(1000L), 40), order(List.of(100L), -1), object(1, 1000, 12)));

        assertThat(state.lastEventId()).isEqualTo(40);
    }

    @Test
    void ignoresMessageTypesItDoesNotModel() {
        var state = ScratchpadState.fromMessages(
                List.of(layer(100, List.of(1000L), 1), new UnknownRoomMessage("sp.somethingNew")));

        assertThat(state.layers()).containsOnlyKeys(100L);
        assertThat(state.lastEventId()).isEqualTo(1);
    }

    @Test
    void toleratesNullsRatherThanThrowing() {
        var state = new ScratchpadState();

        state.applyMessages(null);
        state.applyMessage(null);

        assertThat(state.lastEventId()).isEqualTo(-1);
        assertThat(state.sessionMeta()).isNull();
    }

    @Test
    void indexesTheBotsOwnLayersFramesAndObjectsByTheirLocalIds() {
        var state = ScratchpadState.fromMessages(List.of(
                meta(1, 1),
                layer(0, List.of(0L), 2),
                ownLayer(10, List.of(100L, 101L, 100L), 3, 1, Map.of(5L, 100L, 6L, 101L)),
                object(1, 0, 4),
                ownObject(2, 100, 5, 7, List.of(1.0, 2.0, 3.0, 4.0))));

        assertThat(state.ownLayers().get(1L).layerId()).isEqualTo(10);
        assertThat(state.ownLayers().get(1L).frames()).containsExactly(100L, 101L, 100L);
        assertThat(state.ownFrames().get(5L).frameId()).isEqualTo(100);
        assertThat(state.ownFrames().get(6L).frameId()).isEqualTo(101);
        assertThat(state.ownObjects()).containsOnlyKeys(7L);

        // A replacement arrives under the same objectId, in place.
        state.applyMessage(ownObject(2, 100, 6, 7, List.of(1.0, 2.0)));
        assertThat(state.ownObjects().get(7L).points()).containsExactly(1.0, 2.0);
        assertThat(state.frames().get(100L).objects()).hasSize(1);

        // Frame 6 is dropped, and frame 7 comes.
        state.applyMessage(ownLayer(10, List.of(100L, 102L), 9, 1, Map.of(5L, 100L, 7L, 102L)));
        assertThat(state.ownFrames()).containsOnlyKeys(5L, 7L);

        state.applyMessage(layer(10, List.of(), 10));
        assertThat(state.ownLayers()).isEmpty();
        assertThat(state.ownFrames()).isEmpty();
        assertThat(state.ownObjects()).isEmpty();
    }

    @Test
    void picksTheFrameViewersSeeInEveryAnimationMode() {
        var state = ScratchpadState.fromMessages(List.of(
                meta(1, 1),
                layer(1, List.of(10L, 11L, 10L, 12L), 2),
                new ScratchpadLayerMessage(null, 2, List.of(20L, 21L), AnimationMode.ROLL, 3, null, null),
                new ScratchpadLayerMessage(null, 3, List.of(30L, 31L, 32L), AnimationMode.RANDOM, 4, null, null),
                new ScratchpadRollMessage(null, 0.75, -1)));

        assertThat(state.roll()).isEqualTo(0.75);
        assertThat(state.frames()).hasSize(8);
        assertThat(List.of(0, 1, 2, 3, 4).stream().map(tick -> state.visibleFrameId(1, tick * 125L)))
                .containsExactly(10L, 11L, 10L, 12L, 10L);
        // The ticks start over every 15 minutes: 7200 ticks, not a multiple of 7.
        var sevens = ScratchpadState.fromMessages(
                List.of(meta(1, 1), layer(1, List.of(0L, 1L, 2L, 3L, 4L, 5L, 6L), 2)));
        assertThat(sevens.visibleFrameId(1, 7200 * 125L)).isEqualTo(0L);
        // An older drawing plays at 250 ms a frame.
        var old = ScratchpadState.fromMessages(List.of(
                new ScratchpadSessionMetaMessage(null, 1, 1, 1, 1000, 700, 250), layer(1, List.of(10L, 11L), 2)));
        assertThat(List.of(0L, 125L, 250L).stream().map(time -> old.visibleFrameId(1, time)))
                .containsExactly(10L, 10L, 11L);
        assertThat(state.visibleFrameId(2)).isEqualTo(21L);
        assertThat(state.visibleFrameId(3, 0, 0.5)).isEqualTo(31L);
        assertThat(state.visibleFrameId(404)).isNull();
    }

    @Test
    void aNewSessionForgetsTheRollAndTheBotsOwnDrawing() {
        var state = ScratchpadState.fromMessages(List.of(
                ownLayer(10, List.of(100L), 1, 1, Map.of(5L, 100L)),
                ownObject(2, 100, 2, 7, List.of(1.0, 2.0)),
                new ScratchpadRollMessage(null, 0.5, 3)));

        state.applyMessage(meta(2, 4));

        assertThat(state.roll()).isZero();
        assertThat(state.ownLayers()).isEmpty();
        assertThat(state.ownFrames()).isEmpty();
        assertThat(state.ownObjects()).isEmpty();
    }

    private static RoomMessage ownLayer(
            long layerId, List<Long> frames, long lastEventId, long localId, Map<Long, Long> frameIdsByLocalId) {
        return new ScratchpadLayerMessage(
                null, layerId, frames, AnimationMode.SEQUENTIAL, lastEventId, localId, frameIdsByLocalId);
    }

    private static RoomMessage ownObject(long objectId, long frameId, long eventId, long localId, List<Double> points) {
        return new ScratchpadObjectMessage(
                null, objectId, frameId, eventId, ScratchpadObjectMessage.LINE_FLOATS, 0xff0000ff, 2.0, points, localId);
    }

    private static RoomMessage meta(long session, long eventId) {
        return new ScratchpadSessionMetaMessage(null, session, eventId, 1, 1000, 700, 125);
    }

    private static RoomMessage layer(long layerId, List<Long> frames, long lastEventId) {
        return new ScratchpadLayerMessage(null, layerId, frames, lastEventId);
    }

    private static RoomMessage order(List<Long> order, long lastEventId) {
        return new ScratchpadSetLayerOrderMessage(null, order, lastEventId);
    }

    private static RoomMessage object(long objectId, long frameId, long eventId) {
        return new ScratchpadObjectMessage(
                null, objectId, frameId, eventId, ScratchpadObjectMessage.LINE_FLOATS, 0xff0000ff, 2.0, List.of(1.0, 2.0, 3.0, 4.0));
    }
}
