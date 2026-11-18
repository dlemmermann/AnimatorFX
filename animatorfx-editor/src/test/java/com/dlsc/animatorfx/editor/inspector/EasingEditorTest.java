package com.dlsc.animatorfx.editor.inspector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.doc.Selection.KeyframeRef;
import com.dlsc.animatorfx.editor.inspector.fields.TextValueField;
import com.dlsc.animatorfx.editor.inspector.sections.EasingSection;
import com.dlsc.animatorfx.editor.ui.EditorUiTest;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Easing.CubicBezier;
import com.dlsc.animatorfx.model.Keyframe;
import java.util.List;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EasingEditorTest extends EditorUiTest {

    private static final String OPACITY = "opacity";
    private static final String ROTATION = "transform.rotation";

    private EasingEditor editor;
    private Node curve;
    private ComboBox<String> presets;
    private TextValueField bezier;

    /** Layer a: opacity keyframes k1, k2, k3 and rotation keyframes r1, r2. */
    @BeforeEach
    void content() {
        add(rect("a", 100, 100, 100, 100));
        execute(Commands.setProperty("a", OPACITY, Animatable.keyframed(List.of(
                new Keyframe<>("k1", 0.0, 0.0, Easing.LINEAR), new Keyframe<>("k2", 1.0, 1.0, Easing.LINEAR),
                new Keyframe<>("k3", 2.0, 0.5, Easing.LINEAR)))));
        execute(Commands.setProperty("a", ROTATION, Animatable.keyframed(List.of(
                new Keyframe<>("r1", 0.0, 0.0, Easing.presets().get("Ease Out")),
                new Keyframe<>("r2", 1.0, 90.0, Easing.LINEAR)))));
        select("a");
        editor = window.getSidePanel().getInspector().section(EasingSection.class).orElseThrow().getEditor();
        curve = lookup("#" + EasingEditor.CURVE_ID);
        presets = lookup("#" + EasingEditor.PRESET_ID);
        bezier = lookup("#" + EasingEditor.BEZIER_ID);
    }

    private void selectKeys(String... refs) {
        run(() -> {
            doc.getSelection().keyframes().clear();
            for (String ref : refs) {
                String[] parts = ref.split("/");
                doc.getSelection().keyframes().add(new KeyframeRef("a", parts[0], parts[1]));
            }
        });
        layout();
    }

    private Easing easing(String key, int index) {
        return keyframes("a", key).get(index).easing();
    }

    @Test
    void itShowsTheEasingOfTheSelectedKeyframe() {
        selectKeys(ROTATION + "/r1");
        assertEquals(Easing.presets().get("Ease Out"), fx(editor::getEasing));
        assertEquals("Ease Out", fx(presets::getValue));
        assertEquals("cubic-bezier(0, 0, 0.58, 1)", fx(bezier::getText));
        selectKeys(OPACITY + "/k1");
        assertEquals("Linear", fx(presets::getPromptText));
        assertNull(fx(presets::getValue));
        assertEquals("Linear", fx(() -> presets.getButtonCell().getText()), "the selector shows it");
        assertEquals("cubic-bezier(0, 0, 1, 1)", fx(bezier::getText));
    }

    @Test
    void aPresetAppliesToAllSelectedKeyframesAsOneUndoStep() {
        selectKeys(OPACITY + "/k1", OPACITY + "/k2");
        run(() -> presets.setValue("Ease In Out"));
        layout();
        assertEquals(Easing.presets().get("Ease In Out"), easing(OPACITY, 0));
        assertEquals(Easing.presets().get("Ease In Out"), easing(OPACITY, 1));
        assertEquals(Easing.LINEAR, easing(OPACITY, 2));
        assertEquals("Change Easing", doc.getUndoName());
        assertEquals("cubic-bezier(0.42, 0, 0.58, 1)", fx(bezier::getText));
        run(doc::undo);
        assertEquals(Easing.LINEAR, easing(OPACITY, 0));
        assertEquals(Easing.LINEAR, easing(OPACITY, 1));
    }

    @Test
    void linearAndHoldButtonsSetTheEasing() {
        selectKeys(ROTATION + "/r1");
        run(() -> ((ButtonBase) window.getScene().lookup("#" + EasingEditor.HOLD_ID)).fire());
        layout();
        assertEquals(Easing.HOLD, easing(ROTATION, 0));
        assertEquals("Hold", fx(presets::getPromptText));
        assertEquals("hold", fx(bezier::getText));
        run(() -> ((ButtonBase) window.getScene().lookup("#" + EasingEditor.LINEAR_ID)).fire());
        assertEquals(Easing.LINEAR, easing(ROTATION, 0));
    }

    @Test
    void draggingAHandleChangesTheCurveAsOneUndoStep() {
        selectKeys(ROTATION + "/r1");
        CubicBezier before = (CubicBezier) easing(ROTATION, 0);
        EasingCurve geometry = fx(editor::getGeometry);
        double left = fx(editor::getPlotX);
        // the second handle, from (0.58, 1) to (0.3, 1.2)
        press(curve, left + geometry.x(before.x2()), geometry.y(before.y2()), Keys.NONE);
        dragTo(curve, left + geometry.x(0.45), geometry.y(1.1), Keys.NONE);
        dragTo(curve, left + geometry.x(0.3), geometry.y(1.2), Keys.NONE);
        release(curve, left + geometry.x(0.3), geometry.y(1.2), Keys.NONE);
        CubicBezier after = (CubicBezier) easing(ROTATION, 0);
        assertEquals(before.x1(), after.x1(), 1e-9);
        assertEquals(0.3, after.x2(), 0.01);
        assertEquals(1.2, after.y2(), 0.02);
        assertEquals("Custom", fx(presets::getPromptText));
        assertTrue(fx(bezier::getText).startsWith("cubic-bezier(0, 0, 0.3"));
        run(doc::undo);
        assertEquals(before, easing(ROTATION, 0));

        // far outside the view: x stays within 0..1, y within the overshoot range
        layout();
        press(curve, left + geometry.x(0), geometry.y(0), Keys.NONE);
        dragTo(curve, left - 400, 900, Keys.NONE);
        release(curve, left - 400, 900, Keys.NONE);
        CubicBezier clamped = (CubicBezier) easing(ROTATION, 0);
        assertEquals(0, clamped.x1(), 1e-9);
        assertEquals(EasingCurve.Y_MIN, clamped.y1(), 1e-9);
    }

    @Test
    void aLinearSegmentBecomesACurveWhenAHandleIsDragged() {
        selectKeys(OPACITY + "/k1");
        EasingCurve geometry = fx(editor::getGeometry);
        double left = fx(editor::getPlotX);
        press(curve, left + geometry.x(1), geometry.y(1), Keys.NONE);
        dragTo(curve, left + geometry.x(0.5), geometry.y(1), Keys.NONE);
        release(curve, left + geometry.x(0.5), geometry.y(1), Keys.NONE);
        CubicBezier after = (CubicBezier) easing(OPACITY, 0);
        assertEquals(0.5, after.x2(), 0.01);
        assertEquals(1, after.y2(), 0.02);
        assertEquals(0, after.x1(), 1e-9);
    }

    @Test
    void theNotationCanBeTyped() {
        selectKeys(OPACITY + "/k1");
        run(() -> {
            bezier.requestFocus();
            bezier.setText("cubic-bezier(0.22, 1, 0.36, 1)");
            Event.fireEvent(bezier, new ActionEvent());
        });
        assertEquals(new CubicBezier(0.22, 1, 0.36, 1), easing(OPACITY, 0));
        run(() -> {
            bezier.setText("nonsense");
            Event.fireEvent(bezier, new ActionEvent());
        });
        assertTrue(fx(() -> bezier.getError().isPresent()));
        assertEquals(new CubicBezier(0.22, 1, 0.36, 1), easing(OPACITY, 0));
        run(() -> {
            bezier.setText("hold");
            Event.fireEvent(bezier, new ActionEvent());
        });
        assertEquals(Easing.HOLD, easing(OPACITY, 0));
    }

    @Test
    void differentEasingsShowMixed() {
        selectKeys(OPACITY + "/k1", ROTATION + "/r1");
        assertNull(fx(editor::getEasing));
        assertEquals("Mixed", fx(presets::getPromptText));
        assertEquals("", fx(bezier::getText));
        assertEquals(2, fx(() -> editor.getTargets().size()));
        // a preset makes them the same
        run(() -> presets.setValue("Ease"));
        layout();
        assertEquals(Easing.presets().get("Ease"), fx(editor::getEasing));
    }

    @Test
    void theLastKeyframeOfAPropertyHasNoSegmentToEase() {
        selectKeys(OPACITY + "/k3");
        Node hint = lookup("#" + EasingEditor.HINT_ID);
        assertTrue(fx(hint::isVisible));
        assertTrue(fx(presets::isDisabled));
        assertTrue(fx(() -> editor.getTargets().isEmpty()));
        // together with another keyframe only that one is edited
        selectKeys(OPACITY + "/k3", OPACITY + "/k1");
        assertFalse(fx(hint::isVisible));
        assertEquals(List.of(new KeyframeRef("a", OPACITY, "k1")), fx(editor::getTargets));
        run(() -> presets.setValue("Ease"));
        assertEquals(Easing.LINEAR, easing(OPACITY, 2));
        assertEquals(Easing.presets().get("Ease"), easing(OPACITY, 0));
    }
}
