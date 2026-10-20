package io.github.htearih.scribble.bot;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.htearih.scribble.bot.model.Action;
import io.github.htearih.scribble.bot.model.AnimationMode;
import io.github.htearih.scribble.bot.model.DefaultLayers;
import io.github.htearih.scribble.bot.model.LayerPosition;
import io.github.htearih.scribble.bot.model.PutLayer;
import io.github.htearih.scribble.bot.model.PutObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The drawing actions upstream added in 0.5.0, checked locally before they leave the process. */
class DrawingValidationTest {

    private static final List<Double> LINE = List.of(10.0, 10.0, 90.0, 90.0);

    @Test
    void acceptsADrawingSpecifiedByLocalIds() {
        var actions = List.<Action>of(
                Action.checkSession(1784975936),
                new PutLayer(1, List.of(1L, 2L, 1L), AnimationMode.ROLL, LayerPosition.below(DefaultLayers.FOREGROUND_LAYER_ID)),
                PutObject.onOwnFrame(1, 2, 0xff0000ff, 2, LINE),
                PutObject.onFrame(2, DefaultLayers.BACKGROUND_FRAME_ID, 0xff0000ff, 0, LINE),
                Action.removeObject(3),
                Action.removeLayer(4));

        assertThat(Validation.actions(actions)).isEmpty();
    }

    @Test
    void rejectsZeroLocalIdsWhichDrawingCannotDoWithout() {
        assertThat(Validation.actions(List.of(Action.removeLayer(0))))
                .extracting(ValidationError::path)
                .containsExactly("actions.0.localId");
        assertThat(Validation.actions(List.of(Action.removeObject(Action.MAX_LOCAL_ID + 1))))
                .extracting(ValidationError::path)
                .containsExactly("actions.0.localId");
    }

    @Test
    void rejectsAnEmptyOrOversizedFrameProgram() {
        assertThat(Validation.actions(List.of(new PutLayer(1, List.of()))))
                .extracting(ValidationError::path)
                .containsExactly("actions.0.frameLocalIds");
        assertThat(Validation.actions(List.of(new PutLayer(1, null))))
                .extracting(ValidationError::path)
                .containsExactly("actions.0.frameLocalIds");
        assertThat(Validation.actions(List.of(new PutLayer(1, Collections.nCopies(PutLayer.MAX_FRAMES + 1, 1L)))))
                .extracting(ValidationError::message)
                .containsExactly("must not hold more than 256 frames");
    }

    @Test
    void checksPositionsTakeTheRightNumberOfLayers() {
        assertThat(errors(new PutLayer(1, List.of(1L)).at(new LayerPosition(LayerPosition.At.ABOVE, null, null))))
                .containsExactly("actions.0.position.layerId position above takes exactly one of layerId or layerLocalId");
        assertThat(errors(new PutLayer(1, List.of(1L)).at(new LayerPosition(LayerPosition.At.BELOW, 0L, 2L))))
                .containsExactly("actions.0.position.layerId position below takes exactly one of layerId or layerLocalId");
        assertThat(errors(new PutLayer(1, List.of(1L)).at(new LayerPosition(LayerPosition.At.TOP, null, 2L))))
                .containsExactly("actions.0.position.at position at top takes no layer");
        assertThat(errors(new PutLayer(1, List.of(1L)).at(LayerPosition.above(-1))))
                .containsExactly("actions.0.position.layerId must not be negative");
        assertThat(errors(new PutLayer(1, List.of(1L)).at(LayerPosition.aboveOwn(2)))).isEmpty();
        assertThat(errors(new PutLayer(1, List.of(1L)).at(LayerPosition.bottom()))).isEmpty();
    }

    @Test
    void rejectsAFrameNamedBothWaysOrNotAtAll() {
        assertThat(errors(new PutObject(1, 0L, 1L, 255, 2, List.of(1.0, 2.0))))
                .containsExactly("actions.0.frameId give exactly one of frameId or frameLocalId");
        assertThat(errors(new PutObject(1, null, null, 255, 2, List.of(1.0, 2.0))))
                .containsExactly("actions.0.frameId give exactly one of frameId or frameLocalId");
    }

    @Test
    void checksTheLineItself() {
        assertThat(errors(PutObject.onFrame(1, 0, 255, 2, List.of(1.0, 2.0, 3.0))))
                .containsExactly("actions.0.points points must hold [x, y] pairs");
        assertThat(errors(PutObject.onFrame(1, 0, 255, 2, List.of())))
                .containsExactly("actions.0.points must hold from 1 to 2000 [x, y] pairs");
        assertThat(errors(PutObject.onFrame(1, 0, 255, 2, Collections.nCopies(4002, 1.0))))
                .containsExactly("actions.0.points must hold from 1 to 2000 [x, y] pairs");
        assertThat(errors(PutObject.onFrame(1, 0, 255, 2, List.of(1.0, Double.NaN))))
                .containsExactly("actions.0.points.1 must be a finite number");
        assertThat(errors(PutObject.onFrame(1, 0, 255, 100.5, LINE)))
                .containsExactly("actions.0.lineWidth must be between 0 and 100");
        assertThat(errors(PutObject.onFrame(1, 0, 255, -1, LINE)))
                .containsExactly("actions.0.lineWidth must be between 0 and 100");
        // A single point is a dot, and 2000 pairs is the cap, not past it.
        assertThat(errors(PutObject.onFrame(1, 0, 255, 0, List.of(1.0, 2.0)))).isEmpty();
        assertThat(errors(PutObject.onFrame(1, 0, 255, 100, Collections.nCopies(4000, 1.0)))).isEmpty();
    }

    @Test
    void rejectsAFrameLocalIdGivenToTwoLayersInOneRequest() {
        var clash = Validation.actions(List.of(new PutLayer(1, List.of(1L, 2L)), new PutLayer(2, List.of(2L))));

        assertThat(clash).extracting(ValidationError::toString)
                .containsExactly("actions.1.frameLocalIds frame 2 belongs to layer 1: a frame local ID can't be in two layers");

        // Freed by a new program, or by removing the layer.
        assertThat(Validation.actions(List.of(
                        new PutLayer(1, List.of(1L, 2L)),
                        new PutLayer(1, List.of(1L)),
                        new PutLayer(2, List.of(2L)),
                        Action.removeLayer(1),
                        new PutLayer(3, List.of(1L)))))
                .isEmpty();
    }

    @Test
    void acceptsCheckSessionOnlyBeforeTheOtherDrawingActions() {
        assertThat(Validation.actions(List.of(Action.removeObject(1), Action.checkSession(1))))
                .extracting(ValidationError::toString)
                .containsExactly("actions.1.type sp.checkSession must come before the other sp.* actions");

        // Chat actions don't count.
        assertThat(Validation.actions(List.of(Action.addMessage("hi"), Action.checkSession(1), Action.removeObject(1))))
                .isEmpty();
        assertThat(Validation.actions(List.of(Action.checkSession(0))))
                .extracting(ValidationError::path)
                .containsExactly("actions.0.session");
    }

    @Test
    void capsTheNumberOfActionsAndTheirSize() {
        var atCap = new ArrayList<Action>(Collections.nCopies(Action.MAX_ACTIONS, Action.removeObject(1)));
        assertThat(Validation.actions(atCap)).isEmpty();

        atCap.add(Action.removeObject(1));
        assertThat(Validation.actions(atCap)).extracting(ValidationError::path).containsExactly("actions");

        assertThat(Validation.payloadSize(Action.MAX_PAYLOAD_BYTES)).isEmpty();
        assertThat(Validation.payloadSize(Action.MAX_PAYLOAD_BYTES + 1))
                .extracting(ValidationError::message)
                .containsExactly("take 4194305 bytes of JSON, more than 4194304");
    }

    private static List<String> errors(Action action) {
        return Validation.actions(List.of(action)).stream().map(ValidationError::toString).toList();
    }
}
