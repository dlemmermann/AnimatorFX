package com.dlsc.animatorfx.editor.inspector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dlsc.animatorfx.editor.canvas.tools.NewLayers;
import com.dlsc.animatorfx.editor.doc.Commands;
import com.dlsc.animatorfx.editor.inspector.fields.ColorField;
import com.dlsc.animatorfx.editor.inspector.fields.GradientStopBar;
import com.dlsc.animatorfx.editor.inspector.fields.KeyframeToggle;
import com.dlsc.animatorfx.editor.inspector.fields.NumberField;
import com.dlsc.animatorfx.editor.inspector.fields.PaintField;
import com.dlsc.animatorfx.editor.inspector.fields.TextValueField;
import com.dlsc.animatorfx.editor.inspector.sections.EasingSection;
import com.dlsc.animatorfx.editor.inspector.sections.FillSection;
import com.dlsc.animatorfx.editor.inspector.sections.GeometrySection;
import com.dlsc.animatorfx.editor.inspector.sections.ImageSection;
import com.dlsc.animatorfx.editor.inspector.sections.LayerSection;
import com.dlsc.animatorfx.editor.inspector.sections.SlotSection;
import com.dlsc.animatorfx.editor.inspector.sections.StrokeSection;
import com.dlsc.animatorfx.editor.inspector.sections.TextSection;
import com.dlsc.animatorfx.editor.inspector.sections.TransformSection;
import com.dlsc.animatorfx.editor.ui.EditorUiTest;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.Stroke;
import com.dlsc.animatorfx.model.TextLayer;
import com.dlsc.animatorfx.model.Vec2;
import java.util.List;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InspectorViewTest extends EditorUiTest {

    private static final String POSITION = "transform.position";
    private static final String SCALE = "transform.scale";
    private static final String OPACITY = "opacity";

    private InspectorView inspector;
    private TransformSection transform;

    /** Bottom to top: a and b, 100 x 100 rectangles at (100, 100) and (300, 100). */
    @BeforeEach
    void content() {
        inspector = window.getSidePanel().getInspector();
        transform = inspector.section(TransformSection.class).orElseThrow();
        add(rect("a", 100, 100, 100, 100), rect("b", 300, 100, 100, 100));
        select("a");
    }

    /** Types into a field and commits with Enter. */
    private void type(TextField field, String text) {
        run(() -> {
            field.requestFocus();
            field.setText(text);
            Event.fireEvent(field, new ActionEvent());
        });
        layout();
    }

    private void fire(ButtonBase button) {
        run(button::fire);
        layout();
    }

    private void keyOn(Node node, KeyCode code, boolean shift, boolean alt) {
        run(() -> Event.fireEvent(node, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, alt, false)));
        layout();
    }

    private Vec2 position(String id) {
        return (Vec2) value(id, POSITION).at(doc.getPlayhead());
    }

    private List<String> visibleSections() {
        return fx(() -> inspector.getVisibleSections().stream().map(InspectorSection::title).toList());
    }

    private static String text(TextField field) {
        return fx(field::getText);
    }

    // ------------------------------------------------------------------ sections

    @Test
    void theSectionsFollowTheSelection() {
        assertEquals(List.of("Layer", "Transform", "Geometry", "Fill", "Stroke"), visibleSections());
        select();
        assertEquals(List.of(), visibleSections());
        assertTrue(fx(() -> inspector.lookup(".inspector-empty").isVisible()));

        add(NewLayers.text(doc.getAnimation(), new Vec2(10, 10), "Hello").withBase(
                        NewLayers.text(doc.getAnimation(), Vec2.ZERO, "x").base().withId("t")),
                NewLayers.slot(doc.getAnimation(), "card", new Vec2(50, 50), new Vec2(80, 40)).withBase(
                        NewLayers.slot(doc.getAnimation(), "x", Vec2.ZERO, Vec2.ONE).base().withId("s")),
                group("g", rect("g1", 0, 0, 10, 10)));
        select("t");
        assertEquals(List.of("Layer", "Transform", "Fill", "Stroke", "Text"), visibleSections());
        select("s");
        assertEquals(List.of("Layer", "Transform", "Slot"), visibleSections());
        select("g");
        assertEquals(List.of("Layer", "Transform"), visibleSections());
        // a mixed selection shows what all layers have
        select("a", "t");
        assertEquals(List.of("Layer", "Transform", "Fill", "Stroke"), visibleSections());
        select("a", "s");
        assertEquals(List.of("Layer", "Transform"), visibleSections());
    }

    @Test
    void sectionsCollapse() {
        assertFalse(transform.isCollapsed());
        Node header = fx(() -> transform.node().lookup(".section-header"));
        click(header, 40, 10);
        assertTrue(transform.isCollapsed());
        assertFalse(fx(() -> transform.position().row().isVisible() && transform.position().row().getParent().isVisible()));
        click(header, 40, 10);
        assertFalse(transform.isCollapsed());
    }

    @Test
    void aSectionCanBeAddedForLaterFeatures() {
        class Extra extends SectionBase {
            Extra() {
                super("Effects");
            }

            @Override
            public boolean appliesTo(InspectorContext context) {
                return context.allLayers(ShapeLayer.class);
            }
        }
        run(() -> inspector.getSections().add(2, new Extra()));
        assertEquals(List.of("Layer", "Effects", "Transform", "Geometry", "Fill", "Stroke"), visibleSections());
    }

    // ------------------------------------------------------------------ numbers

    @Test
    void editingPositionXUpdatesTheDocument() {
        NumberField x = transform.position().field().getFirst();
        assertEquals("150px", text(x));
        type(x, "220");
        assertEquals(new Vec2(220, 150), position("a"));
        assertEquals("Change Position", doc.getUndoName());
        assertFalse(value("a", POSITION).isAnimated(), "auto-key is off and the property is static");
        assertEquals("220px", text(x));
        run(doc::undo);
        layout();
        assertEquals(new Vec2(150, 150), position("a"));
        assertEquals("150px", text(x));
    }

    @Test
    void withAutoKeyAnEditAddsAKeyframeAtThePlayhead() {
        run(() -> doc.setAutoKey(true));
        playhead(1.0);
        type(transform.position().field().getSecond(), "300");
        List<? extends Keyframe<?>> keyframes = keyframes("a", POSITION);
        assertEquals(1, keyframes.size());
        assertEquals(1.0, keyframes.get(0).time(), 1e-9);
        assertEquals(new Vec2(150, 300), keyframes.get(0).value());
        assertEquals(KeyframeToggle.State.KEYED, fx(() -> transform.position().toggle().getState()));
    }

    @Test
    void anAnimatedPropertyGetsItsKeyframeAtThePlayheadUpdatedOrAdded() {
        execute(Commands.setProperty("a", OPACITY, Animatable.keyframed(List.of(
                new Keyframe<>("k1", 0.0, 0.0, Easing.LINEAR), new Keyframe<>("k2", 1.0, 1.0, Easing.LINEAR)))));
        playhead(1.0);
        NumberField opacity = transform.opacity().field();
        assertEquals("100%", text(opacity));
        type(opacity, "80");
        assertEquals(0.8, (Double) keyframes("a", OPACITY).get(1).value(), 1e-9);
        assertEquals("k2", keyframes("a", OPACITY).get(1).id());
        playhead(0.5);
        assertEquals("40%", text(opacity));
        assertEquals(KeyframeToggle.State.ANIMATED, fx(() -> transform.opacity().toggle().getState()));
        type(opacity, "10%");
        assertEquals(3, keyframes("a", OPACITY).size());
        assertEquals(0.1, (Double) keyframes("a", OPACITY).get(1).value(), 1e-9);
    }

    @Test
    void scaleAndOpacityAreShownInPercentAndRotationInDegrees() {
        assertEquals("100%", text(transform.scale().field().getFirst()));
        assertEquals("0°", text(transform.rotation().field()));
        run(() -> transform.scale().field().getLinkToggle().setSelected(false));
        type(transform.scale().field().getFirst(), "150");
        assertEquals(new Vec2(1.5, 1), value("a", SCALE).at(0));
        type(transform.rotation().field(), "45°");
        assertEquals(45.0, value("a", "transform.rotation").at(0));
        // opacity is limited to 0..100 %
        type(transform.opacity().field(), "250");
        assertEquals(1.0, value("a", OPACITY).at(0));
        type(transform.opacity().field(), "-5");
        assertEquals(0.0, value("a", OPACITY).at(0));
    }

    @Test
    void linkedScaleKeepsTheProportions() {
        execute(Commands.setProperty("a", SCALE, Animatable.of(new Vec2(1, 0.5))));
        assertTrue(fx(() -> transform.scale().field().getLinkToggle().isSelected()));
        type(transform.scale().field().getFirst(), "200");
        assertEquals(new Vec2(2, 1), value("a", SCALE).at(0));
        type(transform.scale().field().getSecond(), "50");
        assertEquals(new Vec2(1, 0.5), value("a", SCALE).at(0));
    }

    @Test
    void textThatIsNotANumberRevertsAndEscapeAbandonsTheEdit() {
        NumberField x = transform.position().field().getFirst();
        type(x, "abc");
        assertEquals("150px", text(x));
        assertEquals(new Vec2(150, 150), position("a"));
        run(() -> {
            x.requestFocus();
            x.setText("999");
        });
        keyOn(x, KeyCode.ESCAPE, false, false);
        assertEquals("150px", text(x));
        run(() -> canvas.requestFocus());
        assertEquals(new Vec2(150, 150), position("a"));
    }

    @Test
    void losingTheFocusCommits() {
        NumberField x = transform.position().field().getFirst();
        run(() -> {
            x.requestFocus();
            x.setText("175");
        });
        run(() -> canvas.requestFocus());
        layout();
        assertEquals(new Vec2(175, 150), position("a"));
    }

    @Test
    void arrowKeysStepTheValueAsOneUndoStep() {
        NumberField x = transform.position().field().getFirst();
        run(x::requestFocus);
        int steps = fx(() -> doc.isCanUndo()) ? 1 : 0;
        keyOn(x, KeyCode.UP, false, false);
        assertEquals(151, position("a").x(), 1e-9);
        keyOn(x, KeyCode.UP, true, false);
        assertEquals(161, position("a").x(), 1e-9);
        keyOn(x, KeyCode.DOWN, false, true);
        assertEquals(160.9, position("a").x(), 1e-9);
        assertEquals("160.9px", text(x));
        run(() -> canvas.requestFocus());
        run(doc::undo);
        assertEquals(150, position("a").x(), 1e-9, "the three steps undo together");
        assertEquals(steps, 1);
    }

    @Test
    void scrubbingTheLabelChangesTheValueAsOneUndoStep() {
        Node label = fx(() -> transform.rotation().row().getLabel());
        press(label, 10, 10, Keys.NONE);
        dragTo(label, 20, 10, Keys.NONE);
        assertEquals(10.0, (Double) value("a", "transform.rotation").at(0), 1e-9);
        dragTo(label, 40, 10, Keys.NONE);
        assertEquals(30.0, (Double) value("a", "transform.rotation").at(0), 1e-9);
        dragTo(label, 42, 10, Keys.SHIFT);
        assertEquals(320.0, (Double) value("a", "transform.rotation").at(0), 1e-9);
        release(label, 42, 10, Keys.NONE);
        assertEquals("320°", text(transform.rotation().field()));
        assertEquals("Change Rotation", doc.getUndoName());
        run(doc::undo);
        assertEquals(0.0, (Double) value("a", "transform.rotation").at(0), 1e-9);
        assertEquals("Add Layer", doc.getUndoName());
    }

    // ------------------------------------------------------------------ keyframes

    @Test
    void theDiamondAddsThenRemovesAKeyframe() {
        playhead(0.5);
        KeyframeToggle toggle = transform.position().toggle();
        assertEquals(KeyframeToggle.State.NONE, fx(toggle::getState));
        fire(toggle);
        assertEquals(1, keyframes("a", POSITION).size());
        assertEquals(0.5, keyframes("a", POSITION).get(0).time(), 1e-9);
        assertEquals(KeyframeToggle.State.KEYED, fx(toggle::getState));
        assertEquals("Add Keyframe", doc.getUndoName());

        playhead(1.0);
        assertEquals(KeyframeToggle.State.ANIMATED, fx(toggle::getState));
        playhead(0.5);
        fire(toggle);
        assertFalse(value("a", POSITION).isAnimated());
        assertEquals(KeyframeToggle.State.NONE, fx(toggle::getState));
        assertEquals("Delete Keyframe", doc.getUndoName());
    }

    @Test
    void theValuesFollowThePlayheadButNotAnEditInProgress() {
        execute(Commands.setProperty("a", POSITION, Animatable.keyframed(List.of(
                new Keyframe<>("k1", 0.0, new Vec2(100, 100), Easing.LINEAR),
                new Keyframe<>("k2", 2.0, new Vec2(300, 100), Easing.LINEAR)))));
        NumberField x = transform.position().field().getFirst();
        NumberField y = transform.position().field().getSecond();
        playhead(1.0);
        assertEquals("200px", text(x));
        Node section = transform.node();

        // typing in X: the playhead moves, X keeps the typed text and the focus, Y follows
        run(() -> {
            x.requestFocus();
            x.setText("12");
        });
        execute(Commands.setValueAt("a", POSITION, 2.0, new Vec2(300, 180), false));
        playhead(1.5);
        assertEquals("12", text(x));
        assertEquals("160px", text(y));
        assertSame(x, focusOwner());
        assertSame(section, fx(() -> transform.node()), "the section is not rebuilt");
        assertSame(x, transform.position().field().getFirst());
    }

    // ------------------------------------------------------------------ several layers

    @Test
    void aMixedValueShowsThePlaceholderAndAnEditAppliesToAllLayersAsOneUndoStep() {
        select("a", "b");
        NumberField x = transform.position().field().getFirst();
        NumberField y = transform.position().field().getSecond();
        assertEquals("", text(x));
        assertEquals("mixed", fx(x::getPromptText));
        assertEquals("150px", text(y));

        type(y, "400");
        assertEquals(new Vec2(150, 400), position("a"));
        assertEquals(new Vec2(350, 400), position("b"), "each layer keeps its own X");
        run(doc::undo);
        assertEquals(new Vec2(150, 150), position("a"));
        assertEquals(new Vec2(350, 150), position("b"));

        type(x, "10");
        assertEquals(new Vec2(10, 150), position("a"));
        assertEquals(new Vec2(10, 150), position("b"));
        assertEquals("10px", text(x));
    }

    @Test
    void theDiamondWorksOnAllSelectedLayers() {
        select("a", "b");
        execute(Commands.addKeyframe("a", OPACITY, 0));
        KeyframeToggle toggle = transform.opacity().toggle();
        assertEquals(KeyframeToggle.State.ANIMATED, fx(toggle::getState), "only one of the two has a keyframe");
        fire(toggle);
        assertEquals(1, keyframes("b", OPACITY).size());
        assertEquals(KeyframeToggle.State.KEYED, fx(toggle::getState));
        fire(toggle);
        assertFalse(value("a", OPACITY).isAnimated());
        assertFalse(value("b", OPACITY).isAnimated());
    }

    @Test
    void deleteInTheSidePanelNeverDeletesTheLayer() {
        ComboBox<String> type = inspector.section(FillSection.class).orElseThrow().fill().field().getTypeBox();
        run(type::requestFocus);
        assertSame(type, focusOwner());
        key(KeyCode.DELETE);
        key(KeyCode.BACK_SPACE);
        assertEquals(2, layerIds().size());
        // with the focus on the canvas it does
        run(() -> canvas.requestFocus());
        key(KeyCode.DELETE);
        assertEquals(List.of("b"), layerIds());
    }

    // ------------------------------------------------------------------ layer section

    @Test
    void theLayerSectionEditsNameRangeAndFlags() {
        type(lookup("#" + LayerSection.NAME_ID), "Badge");
        assertEquals("Badge", layer("a").name());
        type(lookup("#" + LayerSection.IN_ID), "0.5");
        type(lookup("#" + LayerSection.OUT_ID), "2s");
        assertEquals(0.5, layer("a").base().inPoint(), 1e-9);
        assertEquals(2.0, layer("a").base().outPoint(), 1e-9);
        // the in point stays before the out point, the out point inside the duration
        type(lookup("#" + LayerSection.IN_ID), "5");
        assertEquals(2 - 1 / 60.0, layer("a").base().inPoint(), 1e-9);
        type(lookup("#" + LayerSection.OUT_ID), "9");
        assertEquals(3.0, layer("a").base().outPoint(), 1e-9);

        ToggleButton visible = lookup("#" + LayerSection.VISIBLE_ID);
        assertTrue(fx(visible::isSelected));
        fire(visible);
        assertFalse(layer("a").base().visible());
        assertEquals("Hide Layer", doc.getUndoName());
        fire(lookup("#" + LayerSection.LOCKED_ID));
        assertTrue(layer("a").base().locked());
        run(doc::undo);
        layout();
        assertFalse(fx(() -> ((ToggleButton) window.getScene().lookup("#" + LayerSection.LOCKED_ID)).isSelected()));
    }

    // ------------------------------------------------------------------ geometry

    @Test
    void theGeometrySectionShowsTheFieldsOfTheShapeKind() {
        GeometrySection geometry = inspector.section(GeometrySection.class).orElseThrow();
        assertEquals("100px", text(geometry.size().field().getFirst()));
        type(geometry.size().field().getFirst(), "240");
        assertEquals(new Vec2(240, 100), value("a", "geometry.size").at(0));
        type(geometry.cornerRadius().field(), "12");
        assertEquals(12.0, value("a", "geometry.cornerRadius").at(0));

        add(NewLayers.polygon(doc.getAnimation(), new Vec2(100, 100), 50).withBase(
                        NewLayers.polygon(doc.getAnimation(), Vec2.ZERO, 1).base().withId("p")),
                NewLayers.star(doc.getAnimation(), new Vec2(100, 100), 50).withBase(
                        NewLayers.star(doc.getAnimation(), Vec2.ZERO, 1).base().withId("s")));
        select("p");
        assertFalse(fx(() -> geometry.size().row().isVisible()));
        NumberField sides = lookup("#" + GeometrySection.SIDES_ID);
        assertEquals("3", text(sides));
        type(sides, "6");
        assertEquals(6, ((Geometry.Polygon) ((ShapeLayer) layer("p")).geometry()).sides());
        type(sides, "1");
        assertEquals(3, ((Geometry.Polygon) ((ShapeLayer) layer("p")).geometry()).sides(), "at least a triangle");
        select("s");
        type(lookup("#" + GeometrySection.POINTS_ID), "7");
        assertEquals(7, ((Geometry.Star) ((ShapeLayer) layer("s")).geometry()).points());
        // different kinds of shapes have no geometry in common
        select("p", "s");
        assertFalse(visibleSections().contains("Geometry"));
    }

    // ------------------------------------------------------------------ paint

    @Test
    void theFillIsEditedAsAColour() {
        PaintField fill = inspector.section(FillSection.class).orElseThrow().fill().field();
        ColorField color = fill.getSolidField();
        assertEquals(PaintField.SOLID, fx(() -> fill.getTypeBox().getValue()));
        assertEquals("#FFFFFF", text(color.getHexField()));
        type(color.getHexField(), "#4c9aff");
        assertEquals(new Paint.Solid(Rgba.parse("#4C9AFF")), value("a", "fill").at(0));
        assertEquals("#4C9AFF", text(color.getHexField()));
        type(color.getHexField(), "#4C9AFF80");
        assertEquals(Rgba.parse("#4C9AFF80"), ((Paint.Solid) value("a", "fill").at(0)).color());
        type(color.getHexField(), "nonsense");
        assertEquals("#4C9AFF80", text(color.getHexField()));
        assertEquals("Change Fill", doc.getUndoName());
    }

    @Test
    void theColourPickerChangesTheColourAsOneUndoStep() {
        ColorField color = inspector.section(FillSection.class).orElseThrow().fill().field().getSolidField();
        run(() -> {
            color.getPopup().setColor(Rgba.WHITE);
            color.getPopup().pick(210, 0.5, 1, 1);
            color.getPopup().pick(210, 1, 1, 1);
            color.getPopup().pick(210, 1, 1, 0.5);
            color.getPopup().finishPick();
        });
        layout();
        Rgba picked = ((Paint.Solid) value("a", "fill").at(0)).color();
        assertEquals("#0080FF80", picked.toHex());
        assertEquals("#0080FF80", text(color.getHexField()));
        run(doc::undo);
        assertEquals(new Paint.Solid(Rgba.WHITE), value("a", "fill").at(0));
    }

    @Test
    void theFillTypeSwitchesBetweenNoneSolidAndGradients() {
        PaintField fill = inspector.section(FillSection.class).orElseThrow().fill().field();
        ComboBox<String> type = fill.getTypeBox();
        run(() -> type.setValue(PaintField.LINEAR));
        layout();
        Paint.LinearGradient linear = assertInstanceOf(Paint.LinearGradient.class, value("a", "fill").at(0));
        assertEquals(new Vec2(0, 50), linear.start());
        assertEquals(new Vec2(100, 50), linear.end(), "across the 100 x 100 shape");
        assertEquals(2, linear.stops().size());
        assertEquals(Rgba.WHITE, linear.stops().get(0).color());
        assertTrue(fx(() -> fill.getStopBar().isVisible() && fill.getStartField().getParent().isVisible()));
        assertFalse(fx(() -> fill.getCenterField().getParent().isVisible()));

        run(() -> type.setValue(PaintField.RADIAL));
        layout();
        Paint.RadialGradient radial = assertInstanceOf(Paint.RadialGradient.class, value("a", "fill").at(0));
        assertEquals(new Vec2(50, 50), radial.center());
        assertEquals(50, radial.radius(), 1e-9);
        assertEquals(linear.stops(), radial.stops());
        type(fill.getRadiusField(), "80");
        assertEquals(80, ((Paint.RadialGradient) value("a", "fill").at(0)).radius(), 1e-9);

        run(() -> type.setValue(PaintField.NONE));
        layout();
        assertInstanceOf(Paint.None.class, value("a", "fill").at(0));
        assertFalse(fx(() -> fill.getSolidField().isVisible()));
        run(() -> type.setValue(PaintField.SOLID));
        assertInstanceOf(Paint.Solid.class, value("a", "fill").at(0));
    }

    @Test
    void gradientStopsAreAddedMovedRecolouredAndRemoved() {
        PaintField fill = inspector.section(FillSection.class).orElseThrow().fill().field();
        execute(Commands.setProperty("a", "fill", Animatable.of(new Paint.LinearGradient(new Vec2(0, 0), new Vec2(100, 0),
                List.of(new Paint.Stop(0, Rgba.BLACK), new Paint.Stop(1, Rgba.WHITE))))));
        GradientStopBar bar = fill.getStopBar();
        double middle = fx(() -> bar.offsetToX(0.5));

        // a click on the bar adds a stop with the colour of the gradient there
        click(bar, middle, 6);
        List<Paint.Stop> stops = ((Paint.LinearGradient) value("a", "fill").at(0)).stops();
        assertEquals(3, stops.size());
        assertEquals(0.5, stops.get(1).offset(), 0.011);
        assertEquals(0.5, stops.get(1).color().r(), 0.02);
        assertEquals(1, fx(bar::getSelectedIndex));

        // its colour and offset are edited next to the bar
        type(fill.getStopColorField().getHexField(), "#FF0000");
        assertEquals(Rgba.parse("#FF0000"), ((Paint.LinearGradient) value("a", "fill").at(0)).stops().get(1).color());
        type(fill.getStopOffsetField(), "25");
        assertEquals(0.25, ((Paint.LinearGradient) value("a", "fill").at(0)).stops().get(1).offset(), 1e-9);

        // dragging moves it, as one undo step
        double from = fx(() -> bar.offsetToX(0.25));
        double to = fx(() -> bar.offsetToX(0.75));
        drag(bar, from, 18, to, 18);
        assertEquals(0.75, ((Paint.LinearGradient) value("a", "fill").at(0)).stops().get(1).offset(), 0.011);
        run(doc::undo);
        assertEquals(0.25, ((Paint.LinearGradient) value("a", "fill").at(0)).stops().get(1).offset(), 1e-9);
        layout();

        // dragging it off the bar removes it
        drag(bar, from, 18, from, 90);
        assertEquals(2, ((Paint.LinearGradient) value("a", "fill").at(0)).stops().size());
        // two stops stay: Delete does nothing more, and never deletes the layer
        click(bar, fx(() -> bar.offsetToX(0)), 18);
        key(KeyCode.DELETE);
        assertEquals(2, ((Paint.LinearGradient) value("a", "fill").at(0)).stops().size());
        assertEquals(2, layerIds().size());
    }

    @Test
    void thePointsOfALinearGradientAreNumbers() {
        PaintField fill = inspector.section(FillSection.class).orElseThrow().fill().field();
        execute(Commands.setProperty("a", "fill", Animatable.of(new Paint.LinearGradient(new Vec2(0, 0), new Vec2(100, 0),
                List.of(new Paint.Stop(0, Rgba.BLACK), new Paint.Stop(1, Rgba.WHITE))))));
        type(fill.getEndField().getSecond(), "100");
        type(fill.getStartField().getFirst(), "20");
        Paint.LinearGradient gradient = (Paint.LinearGradient) value("a", "fill").at(0);
        assertEquals(new Vec2(20, 0), gradient.start());
        assertEquals(new Vec2(100, 100), gradient.end());
    }

    // ------------------------------------------------------------------ stroke

    @Test
    void aStrokeIsAddedEditedAndRemoved() {
        StrokeSection section = inspector.section(StrokeSection.class).orElseThrow();
        assertFalse(fx(() -> section.width().row().isVisible()));
        fire(lookup("#" + StrokeSection.ADD_ID));
        Stroke stroke = ((ShapeLayer) layer("a")).stroke();
        assertEquals(2.0, stroke.width().at(0));
        assertEquals("Add Stroke", doc.getUndoName());
        assertTrue(fx(() -> section.width().row().isVisible()));
        assertFalse(fx(() -> window.getScene().lookup("#" + StrokeSection.ADD_ID).isVisible()));

        type(section.width().field(), "6");
        assertEquals(6.0, value("a", "stroke.width").at(0));
        run(() -> ((ComboBox<Stroke.Cap>) window.getScene().lookup("#" + StrokeSection.CAP_ID)).setValue(Stroke.Cap.ROUND));
        run(() -> ((ComboBox<Stroke.Join>) window.getScene().lookup("#" + StrokeSection.JOIN_ID)).setValue(Stroke.Join.BEVEL));
        type(lookup("#" + StrokeSection.MITER_ID), "4");
        TextValueField dash = lookup("#" + StrokeSection.DASH_ID);
        type(dash, "8, 4,2");
        stroke = ((ShapeLayer) layer("a")).stroke();
        assertEquals(Stroke.Cap.ROUND, stroke.cap());
        assertEquals(Stroke.Join.BEVEL, stroke.join());
        assertEquals(4.0, stroke.miterLimit());
        assertEquals(List.of(8.0, 4.0, 2.0), stroke.dashArray());
        assertEquals("8, 4, 2", text(dash));
        // a dash array that cannot be read is refused inline
        run(() -> {
            dash.requestFocus();
            dash.setText("8, x");
        });
        assertTrue(fx(() -> dash.getError().isPresent()));
        run(() -> Event.fireEvent(dash, new ActionEvent()));
        assertEquals(List.of(8.0, 4.0, 2.0), ((ShapeLayer) layer("a")).stroke().dashArray());
        type(dash, "");
        assertEquals(List.of(), ((ShapeLayer) layer("a")).stroke().dashArray());

        type(section.paint().field().getSolidField().getHexField(), "#FFB454");
        assertEquals(new Paint.Solid(Rgba.parse("#FFB454")), value("a", "stroke.paint").at(0));

        fire(lookup("#" + StrokeSection.REMOVE_ID));
        assertNull(((ShapeLayer) layer("a")).stroke());
        assertEquals("Remove Stroke", doc.getUndoName());
    }

    // ------------------------------------------------------------------ text, image, slot

    @Test
    void theTextSectionEditsContentFontAndStyle() {
        add(NewLayers.text(doc.getAnimation(), new Vec2(10, 10), "Hello").withBase(
                NewLayers.text(doc.getAnimation(), Vec2.ZERO, "x").base().withId("t")));
        select("t");
        TextSection section = inspector.section(TextSection.class).orElseThrow();
        type(lookup("#" + TextSection.TEXT_ID), "Payment complete");
        assertEquals("Payment complete", ((TextLayer) layer("t")).text());
        type(section.size().field(), "32");
        assertEquals(32.0, value("t", "fontSize").at(0));

        ComboBox<TextSection.FontChoice> font = lookup("#" + TextSection.FONT_ID);
        assertEquals("System", fx(() -> font.getValue().family()));
        TextSection.FontChoice other = fx(() -> font.getItems().stream()
                .filter(c -> c.assetId() == null && !c.family().equals("System")).findFirst().orElseThrow());
        run(() -> font.setValue(other));
        assertEquals(other.family(), ((TextLayer) layer("t")).fontFamily());
        assertEquals("Change Font", doc.getUndoName());

        fire(lookup("#" + TextSection.BOLD_ID));
        assertEquals(TextLayer.Weight.BOLD, ((TextLayer) layer("t")).fontWeight());
        fire(lookup("#" + TextSection.ITALIC_ID));
        assertTrue(((TextLayer) layer("t")).italic());
        fire(lookup("#inspector-text-align-center"));
        assertEquals(TextLayer.Align.CENTER, ((TextLayer) layer("t")).alignment());
        assertTrue(fx(() -> ((ToggleButton) window.getScene().lookup("#inspector-text-align-center")).isSelected()));
        assertFalse(fx(() -> ((ToggleButton) window.getScene().lookup("#inspector-text-align-left")).isSelected()));
    }

    @Test
    void theSlotSectionValidatesTheNameAndSetsTheRestPose() {
        add(NewLayers.slot(doc.getAnimation(), "card", new Vec2(50, 50), new Vec2(80, 40)).withBase(
                        NewLayers.slot(doc.getAnimation(), "x", new Vec2(50, 50), new Vec2(80, 40)).base().withId("s1")),
                NewLayers.slot(doc.getAnimation(), "button", new Vec2(50, 50), new Vec2(80, 40)).withBase(
                        NewLayers.slot(doc.getAnimation(), "x", new Vec2(50, 50), new Vec2(80, 40)).base().withId("s2")));
        select("s1");
        TextValueField name = lookup("#" + SlotSection.NAME_ID);
        assertEquals("card", text(name));
        for (String bad : List.of("button", "2nd", "class", "")) {
            run(() -> {
                name.requestFocus();
                name.setText(bad);
            });
            assertTrue(fx(() -> name.getError().isPresent()), bad);
            run(() -> Event.fireEvent(name, new ActionEvent()));
            assertEquals("card", ((SlotLayer) layer("s1")).slotName());
        }
        type(name, "header");
        assertEquals("header", ((SlotLayer) layer("s1")).slotName());
        assertEquals("Rename Slot", doc.getUndoName());

        type(lookup("#" + SlotSection.WIDTH_ID), "120");
        assertEquals(120, ((SlotLayer) layer("s1")).width(), 1e-9);

        // the rest pose becomes the transform at the playhead
        execute(Commands.setProperty("s1", POSITION, Animatable.keyframed(List.of(
                new Keyframe<>("k1", 0.0, new Vec2(0, 0), Easing.LINEAR),
                new Keyframe<>("k2", 2.0, new Vec2(200, 0), Easing.LINEAR)))));
        playhead(1.0);
        fire(lookup("#" + SlotSection.REST_ID));
        assertEquals(Animatable.of(new Vec2(100, 0)), ((SlotLayer) layer("s1")).restTransform().position());
        assertEquals("Set Rest Pose", doc.getUndoName());
        // names are unique, so two slots cannot be named at once
        select("s1", "s2");
        assertTrue(fx(name::isDisabled));
    }

    @Test
    void theEasingSectionShowsOnlyWhileKeyframesAreSelected() {
        assertFalse(visibleSections().contains("Easing"));
        execute(Commands.setProperty("a", OPACITY, Animatable.keyframed(List.of(
                new Keyframe<>("k1", 0.0, 0.0, Easing.LINEAR), new Keyframe<>("k2", 1.0, 1.0, Easing.LINEAR)))));
        run(() -> doc.getSelection().keyframes().add(
                new com.dlsc.animatorfx.editor.doc.Selection.KeyframeRef("a", OPACITY, "k1")));
        layout();
        assertEquals("Easing", visibleSections().get(0));
        assertTrue(inspector.section(EasingSection.class).isPresent());
        assertTrue(inspector.section(ImageSection.class).isPresent());
    }
}
