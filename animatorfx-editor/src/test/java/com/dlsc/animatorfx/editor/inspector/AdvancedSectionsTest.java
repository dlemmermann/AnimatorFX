package com.dlsc.animatorfx.editor.inspector;

import com.dlsc.animatorfx.editor.canvas.tools.MotionPathTool;
import com.dlsc.animatorfx.editor.canvas.tools.PathEditTool;
import com.dlsc.animatorfx.editor.canvas.tools.PathTarget;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.inspector.fields.KeyframeToggle;
import com.dlsc.animatorfx.editor.inspector.fields.NumberField;
import com.dlsc.animatorfx.editor.inspector.sections.EffectsSection;
import com.dlsc.animatorfx.editor.inspector.sections.MaskSection;
import com.dlsc.animatorfx.editor.inspector.sections.MotionPathSection;
import com.dlsc.animatorfx.editor.inspector.sections.TrimSection;
import com.dlsc.animatorfx.editor.timeline.TimelineRow;
import com.dlsc.animatorfx.editor.ui.EditorUiTest;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Effect;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.Mask;
import com.dlsc.animatorfx.model.MotionPath;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.Stroke;
import com.dlsc.animatorfx.model.Trim;
import com.dlsc.animatorfx.model.Vec2;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Effects, Mask, Trim and Motion Path sections of the inspector, and what the timeline shows
 * for them, under the headless toolkit.
 */
class AdvancedSectionsTest extends EditorUiTest {

    private InspectorView inspector;
    private EffectsSection effects;

    /** Bottom to top: a and b, 100 x 100 rectangles at (100, 100) and (300, 100). */
    @BeforeEach
    void content() {
        inspector = window.getSidePanel().getInspector();
        effects = inspector.section(EffectsSection.class).orElseThrow();
        add(rect("a", 100, 100, 100, 100), rect("b", 300, 100, 100, 100));
        select("a");
    }

    private void type(TextField field, String text) {
        run(() -> {
            field.requestFocus();
            field.setText(text);
            Event.fireEvent(field, new ActionEvent());
        });
        layout();
    }

    private void fire(String selector) {
        ButtonBase button = lookup(selector);
        run(button::fire);
        layout();
    }

    private void addEffect(String itemId) {
        MenuButton add = lookup("#" + EffectsSection.ADD_ID);
        run(() -> add.getItems().stream().filter(i -> itemId.equals(i.getId())).findFirst().orElseThrow().fire());
        layout();
    }

    private List<Effect> effects(String id) {
        return layer(id).base().effects();
    }

    private NumberField number(SectionBase section, String key) {
        return ((PropertyRows.NumberRow) section.propertyRow(key).orElseThrow()).field();
    }

    private boolean shown(Node node) {
        return fx(() -> {
            for (Node n = node; n != null; n = n.getParent()) {
                if (!n.isVisible()) {
                    return false;
                }
            }
            return node.getScene() != null;
        });
    }

    private List<String> visibleSections() {
        return fx(() -> inspector.getVisibleSections().stream().map(InspectorSection::title).toList());
    }

    private static ShapeLayer stroked(ShapeLayer shape) {
        return shape.withStroke(new Stroke(Animatable.of(new Paint.Solid(Rgba.WHITE)), Animatable.of(2.0),
                Stroke.Cap.BUTT, Stroke.Join.MITER, 10, List.of(), Animatable.of(0.0)));
    }

    // ------------------------------------------------------------------ sections

    @Test
    void theNewSectionsHaveTheirPlaceAndFollowTheSelection() {
        assertEquals(List.of("Layer", "Transform", "Geometry", "Fill", "Stroke", "Trim", "Effects", "Mask", "Motion Path"),
                visibleSections());
        // mask and motion path are about one layer
        select("a", "b");
        assertEquals(List.of("Layer", "Transform", "Geometry", "Fill", "Stroke", "Trim", "Effects"), visibleSections());
        add(group("g", rect("g1", 0, 0, 10, 10)));
        select("g");
        assertEquals(List.of("Layer", "Transform", "Effects", "Mask", "Motion Path"), visibleSections());
    }

    // ------------------------------------------------------------------ effects

    @Test
    void addingABlurThroughTheInspectorAddsTheEffectAndItsPropertyRow() {
        assertTrue(shown(lookup("#" + EffectsSection.HINT_ID)));
        addEffect(EffectsSection.ADD_BLUR_ID);

        assertEquals(List.of(new Effect.Blur(Animatable.of(8.0))), effects("a"));
        NumberField radius = number(effects, "effects[0].radius");
        assertTrue(shown(radius));
        assertEquals("8px", fx(radius::getText));
        assertEquals("Gaussian Blur", ((Label) lookup("#" + EffectsSection.TITLE_ID + 0)).getText());
        assertEquals("Radius", fx(() -> effects.propertyRow("effects[0].radius").orElseThrow().row().getLabel().getText()));

        type(radius, "20");
        assertEquals(20.0, value("a", "effects[0].radius").at(0));
        type(radius, "-5");
        assertEquals(0.0, value("a", "effects[0].radius").at(0), "a radius is not negative");

        run(doc::undo);
        run(doc::undo);
        run(doc::undo);
        layout();
        assertTrue(effects("a").isEmpty(), "adding was one undo step");
        assertTrue(effects.propertyRow("effects[0].radius").isEmpty());
    }

    @Test
    void theEffectsComeWithTheirDefaultsAndUnits() {
        addEffect(EffectsSection.ADD_SHADOW_ID);
        addEffect(EffectsSection.ADD_GLOW_ID);

        assertEquals(List.of(new Effect.DropShadow(Animatable.of(new Rgba(0, 0, 0, 0.5)), Animatable.of(12.0),
                Animatable.of(0.0), Animatable.of(4.0), Animatable.of(0.0)), new Effect.Glow(Animatable.of(0.5))), effects("a"));
        assertEquals("12px", fx(number(effects, "effects[0].radius")::getText));
        assertEquals("4px", fx(number(effects, "effects[0].offsetY")::getText));
        assertEquals("0%", fx(number(effects, "effects[0].spread")::getText));
        assertEquals("50%", fx(number(effects, "effects[1].level")::getText));
        assertTrue(effects.propertyRow("effects[0].color").orElseThrow() instanceof PropertyRows.ColorRow);

        type(number(effects, "effects[1].level"), "250");
        assertEquals(1.0, value("a", "effects[1].level").at(0), "a level stays within 0..100 %");
        type(number(effects, "effects[0].spread"), "30");
        assertEquals(0.3, (Double) value("a", "effects[0].spread").at(0), 1e-9);

        PropertyRows.ColorRow color = (PropertyRows.ColorRow) effects.propertyRow("effects[0].color").orElseThrow();
        type(color.field().getHexField(), "#FF000080");
        assertEquals(Rgba.parse("#FF000080"), value("a", "effects[0].color").at(0));
    }

    @Test
    void anEffectParameterIsKeyframedWithItsDiamondAndShowsInTheTimeline() {
        addEffect(EffectsSection.ADD_BLUR_ID);
        KeyframeToggle toggle = effects.propertyRow("effects[0].radius").orElseThrow().toggle();
        run(toggle::fire);
        playhead(1);
        type(number(effects, "effects[0].radius"), "24");

        assertEquals(2, keyframes("a", "effects[0].radius").size());
        assertEquals(KeyframeToggle.State.KEYED, fx(toggle::getState));
        run(() -> doc.getViewState().getExpandedLayerIds().add("a"));
        layout();
        assertTrue(fx(() -> timeline.getRows().stream().anyMatch(row -> row instanceof TimelineRow.PropertyRow p
                && p.key().equals("effects[0].radius"))));
        Node cell = fx(() -> timeline.getLayerColumn().propertyCell("a", "effects[0].radius").orElseThrow());
        assertEquals("Blur Radius", ((Label) cell.lookup(".property-name")).getText());
        assertEquals("24", ((Label) cell.lookup(".property-value")).getText());
    }

    @Test
    void reorderingAndRemovingKeepTheKeyframesWithTheirEffect() {
        addEffect(EffectsSection.ADD_BLUR_ID);
        addEffect(EffectsSection.ADD_GLOW_ID);
        execute(Commands.setValueAt("a", "effects[0].radius", 1, 30.0, true));
        assertTrue(((ButtonBase) lookup("#" + EffectsSection.UP_ID + 0)).isDisabled());
        assertTrue(((ButtonBase) lookup("#" + EffectsSection.DOWN_ID + 1)).isDisabled());

        fire("#" + EffectsSection.DOWN_ID + 0);

        assertInstanceOf(Effect.Glow.class, effects("a").get(0));
        assertTrue(value("a", "effects[1].radius").isAnimated());
        assertEquals("Glow", ((Label) lookup("#" + EffectsSection.TITLE_ID + 0)).getText());
        assertTrue(shown(number(effects, "effects[1].radius")));
        assertTrue(shown(number(effects, "effects[0].level")));

        fire("#" + EffectsSection.REMOVE_ID + 0);
        assertEquals(1, effects("a").size());
        assertTrue(value("a", "effects[0].radius").isAnimated());
        assertEquals(30.0, value("a", "effects[0].radius").at(1));

        run(doc::undo);
        assertEquals(2, effects("a").size());
        run(doc::undo);
        assertInstanceOf(Effect.Blur.class, effects("a").get(0));
    }

    @Test
    void severalLayersGetAnEffectTogetherAndShowMixedValues() {
        select("a", "b");
        addEffect(EffectsSection.ADD_BLUR_ID);

        assertEquals(1, effects("a").size());
        assertEquals(1, effects("b").size());
        execute(Commands.setValueAt("b", "effects[0].radius", 0, 3.0, false));
        assertEquals("", fx(number(effects, "effects[0].radius")::getText));
        type(number(effects, "effects[0].radius"), "5");
        assertEquals(5.0, value("a", "effects[0].radius").at(0));
        assertEquals(5.0, value("b", "effects[0].radius").at(0));

        select("a");
        addEffect(EffectsSection.ADD_GLOW_ID);
        select("a", "b");
        Label hint = lookup("#" + EffectsSection.HINT_ID);
        assertTrue(shown(hint));
        assertEquals("The selected layers have different effects.", hint.getText());
        assertTrue(effects.propertyRow("effects[0].radius").isEmpty());
    }

    // ------------------------------------------------------------------ mask

    @Test
    void theMaskIsChosenAmongTheSiblingsAndOneUndoRevertsIt() {
        add(group("g", rect("inner", 0, 0, 10, 10)));
        select("a");
        ComboBox<MaskSection.Choice> combo = lookup("#" + MaskSection.LAYER_ID);
        assertEquals(List.of("None", "g", "b"), fx(() -> combo.getItems().stream().map(MaskSection.Choice::name).toList()));
        assertEquals("None", fx(() -> combo.getValue().name()));
        assertFalse(shown(lookup("#" + MaskSection.CLIP_ID)));
        assertFalse(shown(lookup("#" + MaskSection.HINT_ID)));

        run(() -> combo.setValue(combo.getItems().get(2)));
        layout();

        assertEquals(new Mask("b", Mask.Mode.CLIP), layer("a").base().mask());
        assertTrue(fx(((ToggleButton) lookup("#" + MaskSection.CLIP_ID))::isSelected));
        assertTrue(shown(lookup("#" + MaskSection.HINT_ID)));

        fire("#" + MaskSection.ALPHA_ID);
        assertEquals(new Mask("b", Mask.Mode.ALPHA), layer("a").base().mask());
        assertFalse(fx(((ToggleButton) lookup("#" + MaskSection.CLIP_ID))::isSelected));
        fire("#" + MaskSection.ALPHA_ID);
        assertTrue(fx(((ToggleButton) lookup("#" + MaskSection.ALPHA_ID))::isSelected), "a click on the active mode keeps it");

        run(doc::undo);
        assertEquals(Mask.Mode.CLIP, layer("a").base().mask().mode());
        run(doc::undo);
        layout();
        assertNull(layer("a").base().mask());
        assertEquals("None", fx(() -> combo.getValue().name()));

        run(() -> combo.setValue(combo.getItems().get(2)));
        run(() -> combo.setValue(combo.getItems().get(0)));
        layout();
        assertNull(layer("a").base().mask());
    }

    @Test
    void aLayerThatIsMaskedByTheSelectedLayerIsNotOfferedAsItsMask() {
        add(rect("c", 500, 100, 50, 50));
        execute(Commands.setMask("b", new Mask("a", Mask.Mode.CLIP)));
        select("a");
        ComboBox<MaskSection.Choice> combo = lookup("#" + MaskSection.LAYER_ID);

        assertEquals(List.of("None", "c"), fx(() -> combo.getItems().stream().map(MaskSection.Choice::name).toList()));
    }

    @Test
    void convertToPathIsOnlyEnabledForPrimitiveShapes() {
        add(group("g", rect("g1", 0, 0, 10, 10)));
        select("a");
        assertFalse(fx(() -> window.menuItem("menu-edit-convert-to-path").isDisable()));
        select("g");
        assertTrue(fx(() -> window.menuItem("menu-edit-convert-to-path").isDisable()));
        select("a");
        run(() -> window.menuItem("menu-edit-convert-to-path").fire());
        layout();
        assertTrue(fx(() -> window.menuItem("menu-edit-convert-to-path").isDisable()), "a path is a path already");
        run(doc::undo);
        assertFalse(fx(() -> window.menuItem("menu-edit-convert-to-path").isDisable()));
    }

    @Test
    void maskLayerAndMaskedLayerAreMarkedInTheInspectorAndTheTimeline() {
        execute(Commands.setMask("a", new Mask("b", Mask.Mode.CLIP)));

        Label maskedBadge = (Label) fx(() -> timeline.getLayerColumn().layerCell("a").orElseThrow().lookup(".layer-badge"));
        Label maskBadge = (Label) fx(() -> timeline.getLayerColumn().layerCell("b").orElseThrow().lookup(".layer-badge"));
        assertEquals("MASKED", maskedBadge.getText());
        assertEquals("MASK", maskBadge.getText());
        assertTrue(maskBadge.isVisible());

        select("b");
        Label used = lookup("#" + MaskSection.USED_ID);
        assertTrue(shown(used));
        assertTrue(used.getText().contains("\u201ca\u201d"));

        execute(Commands.setMask("a", null));
        assertFalse(maskBadge.isVisible());
        assertFalse(shown(used));
    }

    // ------------------------------------------------------------------ trim

    @Test
    void trimIsEnabledWithItsCheckBoxAndOneUndoRevertsIt() {
        CheckBox enabled = lookup("#" + TrimSection.ENABLED_ID);
        TrimSection trim = inspector.section(TrimSection.class).orElseThrow();
        assertFalse(fx(enabled::isSelected));
        assertFalse(shown(trim.start().row()));
        assertTrue(shown(lookup("#" + TrimSection.HINT_ID)), "without a stroke the trim shows nothing");

        fire("#" + TrimSection.ADD_STROKE_ID);
        assertEquals(2.0, ((ShapeLayer) layer("a")).stroke().width().at(0));
        assertFalse(shown(lookup("#" + TrimSection.HINT_ID)));

        fire("#" + TrimSection.ENABLED_ID);
        assertEquals(new Trim(Animatable.of(0.0), Animatable.of(1.0), Animatable.of(0.0)), ((ShapeLayer) layer("a")).trim());
        assertTrue(fx(enabled::isSelected));
        assertTrue(shown(trim.start().row()));
        assertEquals("100%", fx(trim.end().field()::getText));

        type(trim.end().field(), "50");
        assertEquals(0.5, value("a", "trim.end").at(0));
        type(trim.end().field(), "140");
        assertEquals(1.0, value("a", "trim.end").at(0));
        type(trim.offset().field(), "150");
        assertEquals(1.5, value("a", "trim.offset").at(0), "the offset may go around more than once");
        run(trim.start().toggle()::fire);
        assertTrue(value("a", "trim.start").isAnimated());

        fire("#" + TrimSection.ENABLED_ID);
        assertNull(((ShapeLayer) layer("a")).trim());
        run(doc::undo);
        layout();
        assertTrue(value("a", "trim.start").isAnimated(), "removing the trim is undone with its keyframes");
        assertTrue(fx(enabled::isSelected));
    }

    @Test
    void trimOfSeveralShapesIsSwitchedTogether() {
        execute(Commands.setTrimEnabled("a", true));
        select("a", "b");
        CheckBox enabled = lookup("#" + TrimSection.ENABLED_ID);
        assertTrue(fx(enabled::isIndeterminate));

        fire("#" + TrimSection.ENABLED_ID);

        assertTrue(((ShapeLayer) layer("a")).trim() != null && ((ShapeLayer) layer("b")).trim() != null);
        assertFalse(fx(enabled::isIndeterminate));
        run(doc::undo);
        assertNull(((ShapeLayer) layer("b")).trim());
    }

    // ------------------------------------------------------------------ motion path

    private void clickCanvas(double docX, double docY) {
        Vec2 view = fx(() -> canvas.documentToView(new Vec2(docX, docY)));
        click(canvas, view.x(), view.y());
    }

    @Test
    void aMotionPathIsAddedFromTheInspectorAndOneUndoRevertsIt() {
        MotionPathSection section = inspector.section(MotionPathSection.class).orElseThrow();
        assertFalse(shown(lookup("#" + MotionPathSection.EDIT_ID)));
        assertFalse(shown(section.progress().row()));

        fire("#" + MotionPathSection.ADD_ID);
        assertInstanceOf(MotionPathTool.class, fx(canvas::getModalTool));
        clickCanvas(160, 420);
        clickCanvas(500, 460);
        key(KeyCode.ENTER);

        MotionPath motion = layer("a").base().motionPath();
        assertEquals(2, motion.path().contours().get(0).vertices().size());
        assertEquals(2, motion.progress().keyframes().size());
        assertNull(fx(canvas::getModalTool));
        assertFalse(shown(lookup("#" + MotionPathSection.ADD_ID)));
        assertTrue(shown(lookup("#" + MotionPathSection.EDIT_ID)));
        assertEquals("0%", fx(section.progress().field()::getText));
        assertEquals(KeyframeToggle.State.KEYED, fx(section.progress().toggle()::getState));

        playhead(3);
        assertEquals("100%", fx(section.progress().field()::getText));

        run(doc::undo);
        layout();
        assertNull(layer("a").base().motionPath());
        assertTrue(shown(lookup("#" + MotionPathSection.ADD_ID)));
    }

    @Test
    void theMotionPathIsEditedOrientedAnimatedAndRemovedFromTheInspector() {
        PathData line = new PathData(List.of(new PathData.Contour(List.of(
                new PathData.Vertex(new Vec2(160, 420), Vec2.ZERO, Vec2.ZERO),
                new PathData.Vertex(new Vec2(500, 460), Vec2.ZERO, Vec2.ZERO)), false)));
        execute(Commands.setMotionPath("a", new MotionPath(line, Animatable.of(0.0), false)));
        MotionPathSection section = inspector.section(MotionPathSection.class).orElseThrow();

        fire("#" + MotionPathSection.ORIENT_ID);
        assertTrue(layer("a").base().motionPath().orientToPath());

        type(section.progress().field(), "40");
        assertEquals(0.4, (Double) value("a", "motionPath.progress").at(0), 1e-9);
        type(section.progress().field(), "180");
        assertEquals(1.0, value("a", "motionPath.progress").at(0));

        fire("#" + MotionPathSection.EDIT_ID);
        PathEditTool tool = assertInstanceOf(PathEditTool.class, fx(canvas::getModalTool));
        assertEquals(PathTarget.motionPath("a"), tool.getTarget());
        key(KeyCode.ESCAPE);

        Label hint = lookup("#" + MotionPathSection.HINT_ID);
        assertFalse(hint.getText().contains("keyframes"));
        execute(Commands.setValueAt("a", "transform.position", 1, new Vec2(0, 0), true));
        assertTrue(hint.getText().contains("keyframes"), "position keyframes no longer have an effect");

        fire("#" + MotionPathSection.REMOVE_ID);
        assertNull(layer("a").base().motionPath());
        run(doc::undo);
        assertEquals(line, layer("a").base().motionPath().path());
    }

    // ------------------------------------------------------------------ timeline

    @Test
    void anAnimatedPathShowsAsAPropertyRowWithAPointCount() {
        PathData.Vertex a = new PathData.Vertex(new Vec2(0, 0), Vec2.ZERO, Vec2.ZERO);
        PathData.Vertex b = new PathData.Vertex(new Vec2(100, 0), Vec2.ZERO, Vec2.ZERO);
        PathData.Vertex c = new PathData.Vertex(new Vec2(100, 100), Vec2.ZERO, Vec2.ZERO);
        PathData open = new PathData(List.of(new PathData.Contour(List.of(a, b, c), false)));
        add(stroked(new ShapeLayer(LayerBase.create("p", 3).withId("p"), new Geometry.PathGeometry(Animatable.of(open)),
                Animatable.of(new Paint.None()), null, null)));
        execute(Commands.all("keys", Commands.setValueAt("p", "geometry.path", 0, open, true),
                Commands.setValueAt("p", "geometry.path", 1,
                        new PathData(List.of(new PathData.Contour(List.of(a, b, c, a), false))), true)));
        select("p");
        run(() -> doc.getViewState().getExpandedLayerIds().add("p"));
        layout();

        Node cell = fx(() -> timeline.getLayerColumn().propertyCell("p", "geometry.path").orElseThrow());
        assertEquals("Path", ((Label) cell.lookup(".property-name")).getText());
        assertEquals("3 pts", ((Label) cell.lookup(".property-value")).getText());
        playhead(1);
        assertEquals("4 pts", ((Label) cell.lookup(".property-value")).getText());
        // the keyframes are there to be selected and moved like any others
        int row = fx(() -> timeline.getTrackPane().rowOf("p", "geometry.path"));
        assertTrue(row >= 0);
        assertEquals(List.of(0.0, 1.0), keyframes("p", "geometry.path").stream().map(k -> k.time()).toList());
        // a path layer has no Geometry section: it is edited on the canvas
        assertFalse(fx(() -> inspector.getVisibleSections().stream().anyMatch(s -> s.title().equals("Geometry"))));
    }
}
