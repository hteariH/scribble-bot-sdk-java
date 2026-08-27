package io.github.htearih.scribble.bot.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.htearih.scribble.bot.model.RoomMessage;
import io.github.htearih.scribble.bot.model.ScratchpadLastEventIdMessage;
import io.github.htearih.scribble.bot.model.ScratchpadLayerMessage;
import io.github.htearih.scribble.bot.model.ScratchpadObjectMessage;
import io.github.htearih.scribble.bot.model.ScratchpadSessionMetaMessage;
import io.github.htearih.scribble.bot.model.ScratchpadSetLayerOrderMessage;
import io.github.htearih.scribble.bot.model.UnknownRoomMessage;
import java.util.List;
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

    private static RoomMessage meta(long session, long eventId) {
        return new ScratchpadSessionMetaMessage(null, session, eventId, 1, 1000, 700);
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
