package com.dlsc.animatorfx.player;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Effect;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.player.AnimationPlayer.Status;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.effect.Glow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import static com.dlsc.animatorfx.player.TestDocs.base;
import static com.dlsc.animatorfx.player.TestDocs.doc;
import static com.dlsc.animatorfx.player.TestDocs.group;
import static com.dlsc.animatorfx.player.TestDocs.keys;
import static com.dlsc.animatorfx.player.TestDocs.rect;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(FxThreadExtension.class)
class SlotBindingTest {

    private static final double EPS = 1e-9;

    /** Records what is logged instead of printing it. */
    private static final class RecordingLogger implements System.Logger {

        final List<String> warnings = new ArrayList<>();

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public boolean isLoggable(Level level) {
            return true;
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String message, Throwable thrown) {
            record(level, message);
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... params) {
            record(level, MessageFormat.format(format, params));
        }

        private void record(Level level, String message) {
            if (level == Level.WARNING) {
                warnings.add(message);
            }
        }
    }

    private final ManualClock clock = new ManualClock();
    private final RecordingLogger logger = new RecordingLogger();
    private final AnimationPlayer player = new AnimationPlayer(clock, logger);
    private final AnimationView view = new AnimationView(player);

    /**
     * A slot "card" (80 x 40) that rests at (10, 20), scale 2, rotation 10 and over two seconds
     * moves 100 px right, turns from 10 to 100 degrees and fades to half; its scale is (4, 1).
     */
    private static SlotLayer card() {
        Transform rest = Transform.at(10, 20).withScale(Animatable.of(new Vec2(2, 2)))
                .withRotation(Animatable.of(10.0));
        Transform animated = Transform.IDENTITY
                .withPosition(keys(0, new Vec2(10, 20), 2, new Vec2(110, 20)))
                .withScale(Animatable.of(new Vec2(4, 1)))
                .withRotation(keys(0, 10.0, 2, 100.0));
        return new SlotLayer(base("s").withTransform(animated).withOpacity(keys(0, 1.0, 2, 0.5)), "card", 80, 40,
                rest);
    }

    private static SlotLayer slot(String id, String name) {
        return new SlotLayer(base(id), name, 80, 40, Transform.IDENTITY);
    }

    private static AnimationFile file(Layer... layers) {
        return new AnimationFile(doc(layers), Map.of());
    }

    private void showView() {
        new Scene(new Pane(view), 300, 200);
        view.applyCss();
        view.resize(200, 100);
        view.layout();
    }

    @Test
    void inPlaceBindingDrivesTheNodeRelativeToTheRestTransform() {
        player.setAnimation(file(card()));
        Rectangle node = new Rectangle(80, 40);
        node.setOpacity(0.8);

        player.bindInPlace("card", node);
        player.seek(1);

        assertEquals(50, node.getTranslateX(), EPS);
        assertEquals(0, node.getTranslateY(), EPS);
        assertEquals(2, node.getScaleX(), EPS);
        assertEquals(0.5, node.getScaleY(), EPS);
        assertEquals(45, node.getRotate(), EPS);
        assertEquals(0.8 * 0.75, node.getOpacity(), EPS);
        assertNull(node.getParent());
        assertEquals(Set.of("card"), player.getBoundSlots());
    }

    @Test
    void inPlaceBindingAppliesTheCurrentFrameAtOnce() {
        player.setAnimation(file(card()));
        player.seek(2);
        Rectangle node = new Rectangle(80, 40);

        player.bindInPlace("card", node);

        assertEquals(100, node.getTranslateX(), EPS);
        assertEquals(0.5, node.getOpacity(), EPS);
    }

    @Test
    void inPlaceBindingFollowsPlaybackWithoutAView() {
        ManualClock clock = new ManualClock();
        AnimationPlayer alone = new AnimationPlayer(clock);
        alone.setAnimation(file(card()));
        Rectangle node = new Rectangle(80, 40);
        alone.bindInPlace("card", node);

        alone.play();
        clock.tick(0.5);

        assertEquals(25, node.getTranslateX(), EPS);
    }

    @Test
    void aZeroRestScaleDoesNotDivideByZero() {
        player.setAnimation(file(new SlotLayer(base("s"), "card", 80, 40,
                Transform.IDENTITY.withScale(Animatable.of(Vec2.ZERO)))));
        Rectangle node = new Rectangle(80, 40);

        player.bindInPlace("card", node);

        assertEquals(1, node.getScaleX(), EPS);
        assertEquals(1, node.getScaleY(), EPS);
    }

    @Test
    void inPlaceBindingIgnoresParentGroupsButFollowsTheSlotsVisibility() {
        SlotLayer late = new SlotLayer(base("s").withInPoint(1), "card", 80, 40, Transform.IDENTITY);
        player.setAnimation(file(TestDocs.at(group("g", late), 500, 500)));
        Rectangle node = new Rectangle(80, 40);

        player.bindInPlace("card", node);

        assertEquals(0, node.getTranslateX(), EPS);
        assertFalse(node.isVisible());

        player.seek(1.5);
        assertTrue(node.isVisible());
    }

    @Test
    void inPlaceBindingMapsEffectsLikeTheRenderer() {
        SlotLayer blurred = new SlotLayer(base("s").withEffects(List.of(new Effect.Blur(keys(0, 0.0, 2, 10.0)))),
                "card", 80, 40, Transform.IDENTITY);
        player.setAnimation(file(blurred));
        Rectangle node = new Rectangle(80, 40);
        Glow original = new Glow();
        node.setEffect(original);

        player.bindInPlace("card", node);
        player.seek(1);

        GaussianBlur blur = assertInstanceOf(GaussianBlur.class, node.getEffect());
        assertEquals(5, blur.getRadius(), EPS);

        player.unbind("card");
        assertSame(original, node.getEffect());
    }

    @Test
    void aSlotWithoutEffectsLeavesTheNodesEffectAlone() {
        player.setAnimation(file(card()));
        Rectangle node = new Rectangle(80, 40);
        Glow original = new Glow();
        node.setEffect(original);

        player.bindInPlace("card", node);
        player.seek(1);

        assertSame(original, node.getEffect());
    }

    @Test
    void unbindRestoresEveryChangedProperty() {
        SlotLayer late = card().withBase(card().base().withInPoint(1.5));
        player.setAnimation(file(late));
        Rectangle node = new Rectangle(80, 40);
        node.setTranslateX(3);
        node.setTranslateY(4);
        node.setScaleX(1.5);
        node.setScaleY(2.5);
        node.setRotate(7);
        node.setOpacity(0.8);

        player.bindInPlace("card", node);
        player.seek(1);
        assertFalse(node.isVisible());
        player.unbind("card");

        assertEquals(3, node.getTranslateX(), EPS);
        assertEquals(4, node.getTranslateY(), EPS);
        assertEquals(1.5, node.getScaleX(), EPS);
        assertEquals(2.5, node.getScaleY(), EPS);
        assertEquals(7, node.getRotate(), EPS);
        assertEquals(0.8, node.getOpacity(), EPS);
        assertTrue(node.isVisible());
        assertEquals(Set.of(), player.getBoundSlots());

        player.seek(2);
        assertEquals(3, node.getTranslateX(), EPS, "an unbound node is no longer driven");
    }

    @Test
    void unbindingAnUnboundSlotDoesNothing() {
        player.setAnimation(file(card()));
        player.unbind("card");
        player.unbind("nope");

        assertEquals(Set.of(), player.getBoundSlots());
    }

    @Test
    void bindingAnUnknownSlotIsRejected() {
        player.setAnimation(file(card()));

        assertThrows(IllegalArgumentException.class, () -> player.bindInPlace("nope", new Rectangle()));
        assertThrows(IllegalArgumentException.class, () -> view.bind("nope", new Rectangle()));
    }

    @Test
    void bindingWithoutAnAnimationIsRejected() {
        assertThrows(IllegalStateException.class, () -> player.bindInPlace("card", new Rectangle()));
        assertThrows(IllegalStateException.class, () -> view.bind("card", new Rectangle()));
    }

    @Test
    void rebindingRestoresTheFirstNode() {
        player.setAnimation(file(card()));
        Rectangle first = new Rectangle(80, 40);
        Rectangle second = new Rectangle(80, 40);
        player.bindInPlace("card", first);
        player.seek(1);

        player.bindInPlace("card", second);

        assertEquals(0, first.getTranslateX(), EPS);
        assertEquals(1, first.getOpacity(), EPS);
        assertEquals(50, second.getTranslateX(), EPS);

        player.seek(2);
        assertEquals(0, first.getTranslateX(), EPS);
        assertEquals(100, second.getTranslateX(), EPS);
    }

    @Test
    void embeddedBindingParentsTheNodeIntoTheSlotContainerAndResizesARegion() {
        player.setAnimation(file(card()));
        showView();
        Region region = new Region();
        Pane formerParent = new Pane(region);

        view.bind("card", region);

        assertSame(view.getLayerNode("s"), region.getParent());
        assertTrue(formerParent.getChildren().isEmpty());
        assertEquals(80, region.getWidth(), EPS);
        assertEquals(40, region.getHeight(), EPS);
        assertEquals(0, region.getTranslateX(), EPS, "the container carries the transform, not the node");
        assertEquals(Set.of("card"), player.getBoundSlots());
    }

    @Test
    void embeddedNodeBoundBeforeTheSkinExistsIsAttachedWithTheSkin() {
        player.setAnimation(file(card()));
        Region region = new Region();
        view.bind("card", region);
        assertNull(region.getParent());

        showView();

        assertSame(view.getLayerNode("s"), region.getParent());
        assertEquals(80, region.getWidth(), EPS);
    }

    @Test
    void embeddedNodeMovesToTheNewContainerWhenTheDocumentIsRebuilt() {
        player.setAnimation(file(card()));
        showView();
        Region region = new Region();
        view.bind("card", region);
        Node firstContainer = view.getLayerNode("s");

        player.setAnimation(file(rect("r", 10, 10), card().withWidth(60)));

        assertTrue(view.getLayerNode("s") != firstContainer, "the structure change rebuilt the nodes");
        assertSame(view.getLayerNode("s"), region.getParent());
        assertEquals(60, region.getWidth(), EPS);
    }

    @Test
    void aReplacedSkinTakesOverTheEmbeddedNodes() {
        player.setAnimation(file(card()));
        showView();
        Region first = new Region();
        view.bind("card", first);

        view.setSkin(new AnimationViewSkin(view));
        assertSame(view.getLayerNode("s"), first.getParent());

        Region second = new Region();
        view.bind("card", second);
        assertSame(view.getLayerNode("s"), second.getParent());
        assertNull(first.getParent());
    }

    @Test
    void unbindRemovesAnEmbeddedNodeFromTheViewAndKeepsItsSize() {
        player.setAnimation(file(card()));
        showView();
        Region region = new Region();
        view.bind("card", region);

        view.unbind("card");

        assertNull(region.getParent());
        assertTrue(((Group) view.getLayerNode("s")).getChildren().isEmpty());
        assertEquals(80, region.getWidth(), EPS);
        assertEquals(Set.of(), player.getBoundSlots());
    }

    @Test
    void aSlotHasOneBindingAcrossBothModes() {
        player.setAnimation(file(card()));
        showView();
        Region embedded = new Region();
        Rectangle inPlace = new Rectangle(80, 40);
        view.bind("card", embedded);

        player.bindInPlace("card", inPlace);
        player.seek(1);

        assertNull(embedded.getParent());
        assertEquals(50, inPlace.getTranslateX(), EPS);

        view.bind("card", embedded);

        assertEquals(0, inPlace.getTranslateX(), EPS);
        assertSame(view.getLayerNode("s"), embedded.getParent());
        assertEquals(Set.of("card"), player.getBoundSlots());
    }

    @Test
    void autoBindMatchesByNodeId() {
        player.setAnimation(file(card(), slot("o", "other"), slot("m", "missing")));
        Rectangle cardNode = new Rectangle(80, 40);
        cardNode.setId("card");
        Rectangle otherNode = new Rectangle(80, 40);
        otherNode.setId("other");
        Pane root = new Pane(new Group(cardNode), otherNode);
        player.seek(1);

        view.autoBind(root);

        assertEquals(Set.of("card", "other"), player.getBoundSlots());
        assertEquals(50, cardNode.getTranslateX(), EPS);
        assertSame(root, otherNode.getParent(), "bound in place");
    }

    @Test
    void autoBindLeavesBoundSlotsAlone() {
        player.setAnimation(file(card()));
        Rectangle explicit = new Rectangle(80, 40);
        Rectangle byId = new Rectangle(80, 40);
        byId.setId("card");
        player.bindInPlace("card", explicit);
        player.seek(1);

        view.autoBind(new Pane(byId));

        assertEquals(50, explicit.getTranslateX(), EPS);
        assertEquals(0, byId.getTranslateX(), EPS);
    }

    @Test
    void autoBindResolvesTheFxmlNamespaceFirst() {
        player.setAnimation(file(card(), slot("o", "other"), slot("t", "text")));
        Rectangle fromNamespace = new Rectangle(80, 40);
        Rectangle cardById = new Rectangle(80, 40);
        cardById.setId("card");
        Rectangle otherById = new Rectangle(80, 40);
        otherById.setId("other");
        Pane root = new Pane(cardById, otherById);
        player.seek(1);

        view.autoBind(root, Map.of("card", fromNamespace, "text", "not a node"));

        assertEquals(Set.of("card", "other"), player.getBoundSlots());
        assertEquals(50, fromNamespace.getTranslateX(), EPS);
        assertEquals(0, cardById.getTranslateX(), EPS);
    }

    @Test
    void changingTheAnimationKeepsBindingsWhoseSlotStillExists() {
        player.setAnimation(file(card(), slot("o", "other")));
        Rectangle cardNode = new Rectangle(80, 40);
        Rectangle otherNode = new Rectangle(80, 40);
        otherNode.setOpacity(0.5);
        player.bindInPlace("card", cardNode);
        player.bindInPlace("other", otherNode);
        player.seek(1);

        Animation replacement = doc(TestDocs.at(new SlotLayer(base("x"), "card", 80, 40, Transform.IDENTITY), 7, 0),
                new SlotLayer(base("y").withOpacity(Animatable.of(0.0)), "third", 80, 40, Transform.IDENTITY));
        player.setAnimation(new AnimationFile(replacement, Map.of()));

        assertEquals(Set.of("card"), player.getBoundSlots());
        assertEquals(7, cardNode.getTranslateX(), EPS, "re-applied against the new document");
        assertEquals(0.5, otherNode.getOpacity(), EPS, "restored");

        player.setAnimation(null);
        assertEquals(Set.of(), player.getBoundSlots());
        assertEquals(0, cardNode.getTranslateX(), EPS);
    }

    @Test
    void anUnboundSlotIsLoggedOncePerSlotAndPlaybackContinues() {
        player.setAnimation(file(card(), slot("o", "other")));
        // the in-place binding also keeps the player of the hidden view running
        player.bindInPlace("other", new Rectangle());
        assertEquals(List.of(), logger.warnings, "nothing is logged before playback");

        player.play();
        clock.tick(0.5);
        clock.tick(0.5);

        assertEquals(1, logger.warnings.size());
        assertTrue(logger.warnings.get(0).contains("card"), logger.warnings.get(0));
        assertEquals(1, player.getCurrentTime(), EPS);
        assertEquals(Status.PLAYING, player.getStatus());
    }
}
