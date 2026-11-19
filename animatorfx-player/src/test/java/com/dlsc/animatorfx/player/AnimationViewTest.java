package com.dlsc.animatorfx.player;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.player.AnimationPlayer.Status;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Affine;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static com.dlsc.animatorfx.player.TestDocs.doc;
import static com.dlsc.animatorfx.player.TestDocs.keys;
import static com.dlsc.animatorfx.player.TestDocs.rect;
import static com.dlsc.animatorfx.player.TestDocs.transformed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(FxThreadExtension.class)
class AnimationViewTest {

    private static final double EPS = 1e-9;

    private final ManualClock clock = new ManualClock();
    private final AnimationPlayer player = new AnimationPlayer(clock);
    private final AnimationView view = new AnimationView(player);

    /** A 200 x 100 document, two seconds, with a 10 x 10 rectangle "r" moving from x = 0 to x = 100. */
    private static AnimationFile moving() {
        return file(doc(transformed(rect("r", 10, 10),
                Transform.IDENTITY.withPosition(keys(0, new Vec2(0, 0), 2, new Vec2(100, 0))))));
    }

    private static AnimationFile file(Animation animation) {
        return new AnimationFile(animation, Map.of());
    }

    private void layOut(double width, double height) {
        if (view.getScene() == null) {
            new Scene(new Pane(view), 500, 500);
        }
        view.applyCss();
        view.resize(width, height);
        view.layout();
    }

    private static void assertTransform(Affine transform, double scale, double tx, double ty) {
        assertEquals(scale, transform.getMxx(), EPS);
        assertEquals(scale, transform.getMyy(), EPS);
        assertEquals(0, transform.getMxy(), EPS);
        assertEquals(0, transform.getMyx(), EPS);
        assertEquals(tx, transform.getTx(), EPS);
        assertEquals(ty, transform.getTy(), EPS);
    }

    private Affine contentTransform() {
        return assertInstanceOf(Affine.class, view.getContentTransform());
    }

    @Test
    void containFitsTheWholeDocumentAndCentresIt() {
        view.setAnimation(moving());
        view.resize(400, 400);

        assertEquals(Fit.CONTAIN, view.getFit());
        assertTransform(contentTransform(), 2, 0, 100);
    }

    @Test
    void coverFillsTheViewAndCentresTheDocument() {
        view.setAnimation(moving());
        view.setFit(Fit.COVER);
        view.resize(400, 400);

        assertTransform(contentTransform(), 4, -200, 0);
    }

    @Test
    void noneKeepsTheDocumentSizeAndCentresIt() {
        view.setAnimation(moving());
        view.setFit(Fit.NONE);
        view.resize(400, 400);

        assertTransform(contentTransform(), 1, 100, 150);
    }

    @Test
    void contentTransformWithoutAnAnimationIsTheIdentity() {
        view.resize(400, 400);

        assertTransform(contentTransform(), 1, 0, 0);
    }

    @Test
    void contentTransformRespectsThePadding() {
        view.setAnimation(moving());
        view.setPadding(new Insets(0, 0, 0, 200));
        view.resize(400, 400);

        assertTransform(contentTransform(), 1, 200, 150);
    }

    @Test
    void theRenderedContentUsesTheContentTransform() {
        view.setAnimation(moving());
        view.seek(1);
        layOut(400, 400);

        Node layer = view.getLayerNode("r");
        Point2D inView = view.sceneToLocal(layer.localToScene(0, 0));

        assertEquals(new Point2D(100, 100), inView);
        assertEquals(inView, view.getContentTransform().transform(50, 0));

        view.setFit(Fit.NONE);
        view.layout();
        assertEquals(new Point2D(150, 150), view.sceneToLocal(layer.localToScene(0, 0)));
    }

    @Test
    void theContentIsClippedToTheView() {
        view.setAnimation(moving());
        view.setFit(Fit.COVER);
        layOut(400, 400);

        assertEquals(400, view.getBoundsInLocal().getWidth(), EPS);
        assertEquals(0, view.getBoundsInLocal().getMinX(), EPS);
    }

    @Test
    void preferredSizeIsTheDocumentSize() {
        view.setAnimation(moving());
        layOut(400, 400);

        assertEquals(200, view.prefWidth(-1), EPS);
        assertEquals(100, view.prefHeight(-1), EPS);
        assertEquals(0, view.minWidth(-1), EPS);
        assertEquals(0, view.minHeight(-1), EPS);
        assertEquals(Double.MAX_VALUE, view.maxWidth(-1), EPS);
        assertEquals(Double.MAX_VALUE, view.maxHeight(-1), EPS);
    }

    @Test
    void preferredSizeWithoutAnAnimationIsZero() {
        layOut(400, 400);

        assertEquals(0, view.prefWidth(-1), EPS);
        assertEquals(0, view.prefHeight(-1), EPS);
    }

    @Test
    void aParentLaysTheViewOutAtTheDocumentSize() {
        view.setAnimation(moving());
        StackPane parent = new StackPane(view);
        Pane root = new Pane(parent);
        new Scene(root, 500, 500);
        root.applyCss();
        parent.autosize();
        root.layout();

        assertEquals(200, view.getWidth(), EPS);
        assertEquals(100, view.getHeight(), EPS);

        view.setAnimation(file(Animation.create("bigger", 300, 150, 1)));
        parent.autosize();
        root.layout();
        assertEquals(300, view.getWidth(), EPS);
    }

    @Test
    void hasTheStyleClassAndTheSkin() {
        assertTrue(view.getStyleClass().contains("animation-view"));
        assertFalse(view.isFocusTraversable());
        assertNull(view.getSkin());

        layOut(400, 400);

        assertInstanceOf(AnimationViewSkin.class, view.getSkin());
        assertFalse(view.isFocusTraversable());
    }

    @Test
    void fitIsStyleable() {
        view.setStyle("-fx-fit: cover;");
        layOut(400, 400);
        assertEquals(Fit.COVER, view.getFit());

        view.setStyle("-fx-fit: none;");
        view.applyCss();
        assertEquals(Fit.NONE, view.getFit());

        view.setStyle("-fx-fit: contain;");
        view.applyCss();
        assertEquals(Fit.CONTAIN, view.getFit());
    }

    @Test
    void autoPlayIsStyleable() {
        assertFalse(view.isAutoPlay());

        view.setStyle("-fx-auto-play: true;");
        layOut(400, 400);

        assertTrue(view.isAutoPlay());
    }

    @Test
    void layerNodeIsAvailableOnceTheSkinExists() {
        view.setAnimation(moving());
        assertNull(view.getLayerNode("r"));

        layOut(400, 400);

        assertNotNull(view.getLayerNode("r"));
        assertNull(view.getLayerNode("nope"));
    }

    @Test
    void theSkinRendersTheCurrentFrameAndFollowsThePlayer() {
        view.setAnimation(moving());
        view.seek(1);
        layOut(200, 100);
        Node layer = view.getLayerNode("r");

        assertEquals(50, layer.getLocalToParentTransform().getTx(), EPS);

        view.seek(2);
        assertEquals(100, layer.getLocalToParentTransform().getTx(), EPS);

        view.setAnimation(file(doc(TestDocs.at(rect("r", 10, 10), 30, 0))));
        assertSame(layer, view.getLayerNode("r"));
        assertEquals(30, layer.getLocalToParentTransform().getTx(), EPS);

        view.setAnimation(null);
        assertNull(view.getLayerNode("r"));
    }

    @Test
    void delegatesToThePlayer() {
        assertSame(player, view.getPlayer());
        assertSame(player.animationProperty(), view.animationProperty());
        assertSame(player.currentTimeProperty(), view.currentTimeProperty());
        assertSame(player.rateProperty(), view.rateProperty());
        assertSame(player.cycleCountProperty(), view.cycleCountProperty());
        assertSame(player.autoReverseProperty(), view.autoReverseProperty());
        assertSame(player.statusProperty(), view.statusProperty());

        AnimationFile file = moving();
        AnimationView other = new AnimationView(file);
        assertSame(file, other.getAnimation());
        assertSame(file, other.getPlayer().getAnimation());
    }

    @Test
    void playbackIsSuspendedWhileTheViewIsNotShowing() {
        view.setAnimation(moving());
        view.setCycleCount(AnimationPlayer.INDEFINITE);
        view.play();

        assertEquals(Status.PLAYING, view.getStatus());
        assertFalse(clock.running);

        Stage stage = new Stage();
        stage.setScene(new Scene(new Pane(view), 300, 200));
        stage.show();
        try {
            assertTrue(clock.running);
            clock.tick(0.5);
            assertEquals(0.5, view.getCurrentTime(), EPS);

            stage.hide();
            assertFalse(clock.running);
            assertEquals(Status.PLAYING, view.getStatus());

            stage.show();
            assertTrue(clock.running);

            ((Pane) stage.getScene().getRoot()).getChildren().clear();
            assertFalse(clock.running);
        } finally {
            stage.hide();
        }
    }

    @Test
    void aPlayerWithInPlaceBindingsKeepsRunningWhileTheViewIsHidden() {
        view.setAnimation(file(doc(new SlotLayer(TestDocs.base("s"), "card", 10, 10,
                Transform.IDENTITY))));
        view.play();
        assertFalse(clock.running);

        view.autoBind(new Pane(), Map.of("card", new Rectangle()));
        assertTrue(clock.running);

        view.unbind("card");
        assertFalse(clock.running);
    }

    @Test
    void autoPlayStartsWhenTheViewIsShown() {
        view.setAnimation(moving());
        view.setAutoPlay(true);
        assertEquals(Status.STOPPED, view.getStatus());

        Stage stage = new Stage();
        stage.setScene(new Scene(new Pane(view), 300, 200));
        stage.show();
        try {
            assertEquals(Status.PLAYING, view.getStatus());
            assertTrue(clock.running);
        } finally {
            stage.hide();
        }
    }

    @Test
    void autoPlayStartsWhenAnAnimationIsSetOnAShowingView() {
        view.setAutoPlay(true);
        Stage stage = new Stage();
        stage.setScene(new Scene(new Pane(view), 300, 200));
        stage.show();
        try {
            assertEquals(Status.STOPPED, view.getStatus());

            view.setAnimation(moving());

            assertEquals(Status.PLAYING, view.getStatus());
        } finally {
            stage.hide();
        }
    }

    @Test
    void withoutAutoPlayShowingDoesNotStartPlayback() {
        view.setAnimation(moving());
        Stage stage = new Stage();
        stage.setScene(new Scene(new Pane(view), 300, 200));
        stage.show();
        try {
            assertEquals(Status.STOPPED, view.getStatus());
        } finally {
            stage.hide();
        }
    }

    @Test
    void soundSettingsAndScrubbingAreThePlayers() {
        view.setAnimation(moving());

        assertSame(player.mutedProperty(), view.mutedProperty());
        assertSame(player.volumeProperty(), view.volumeProperty());
        assertFalse(view.isMuted());
        assertEquals(1, view.getVolume(), EPS);

        view.setMuted(true);
        view.setVolume(0.3);
        view.scrub(1.5);

        assertTrue(player.isMuted());
        assertEquals(0.3, player.getVolume(), EPS);
        assertEquals(1.5, player.getCurrentTime(), EPS);
    }

    /** A view on a player whose sound is recorded, playing a document with one audio layer. */
    private static AnimationView sounding(ManualClock clock,
                                          com.dlsc.animatorfx.player.internal.audio.RecordingAudioOutput output) {
        AnimationPlayer player = new AnimationPlayer(clock, System.getLogger("test"), output);
        AnimationView view = new AnimationView(player);
        view.setAnimation(file(doc(rect("card", 10, 10), new com.dlsc.animatorfx.model.AudioLayer(TestDocs.base("music"),
                "snd", 0, com.dlsc.animatorfx.model.Animatable.of(1.0), com.dlsc.animatorfx.model.Animatable.of(0.0),
                false, false))));
        view.setCycleCount(AnimationPlayer.INDEFINITE);
        return view;
    }

    @Test
    void disposeReleasesTheSoundOfThePlayer() {
        var output = new com.dlsc.animatorfx.player.internal.audio.RecordingAudioOutput();
        AnimationView sounding = sounding(clock, output);
        Stage stage = new Stage();
        stage.setScene(new Scene(new Pane(sounding), 300, 200));
        stage.show();
        try {
            sounding.play();
            clock.tick(0.1);
            sounding.dispose();
            clock.tick(0.1);

            assertEquals(1, output.count("dispose"));
            assertEquals(1, output.applied.size());
        } finally {
            stage.hide();
        }
    }

    @Test
    void aViewThatLeavesItsSceneReleasesItsSoundAndGetsItBackWhenItReturns() {
        var output = new com.dlsc.animatorfx.player.internal.audio.RecordingAudioOutput();
        AnimationView sounding = sounding(clock, output);
        Stage stage = new Stage();
        Pane root = new Pane(sounding);
        stage.setScene(new Scene(root, 300, 200));
        stage.show();
        try {
            sounding.play();
            clock.tick(0.1);

            stage.hide();
            assertEquals(0, output.count("dispose"), "a hidden view keeps its sound ready");
            stage.show();

            root.getChildren().clear();
            assertEquals(1, output.count("dispose"));

            root.getChildren().add(sounding);
            clock.tick(0.1);
            assertEquals(2, output.applied.size());
        } finally {
            stage.hide();
        }
    }

    @Test
    void aViewThatDrivesNodesInPlaceKeepsItsSoundWhenItLeavesItsScene() {
        var output = new com.dlsc.animatorfx.player.internal.audio.RecordingAudioOutput();
        AnimationView sounding = sounding(clock, output);
        sounding.setAnimation(file(sounding.getAnimation().animation().withLayers(List.of(
                sounding.getAnimation().animation().layers().get(1),
                new SlotLayer(TestDocs.base("s"), "card", 10, 10, Transform.IDENTITY)))));
        Stage stage = new Stage();
        Pane root = new Pane(sounding);
        stage.setScene(new Scene(root, 300, 200));
        stage.show();
        try {
            sounding.getPlayer().bindInPlace("card", new Rectangle(10, 10));
            sounding.play();
            clock.tick(0.1);
            root.getChildren().clear();
            clock.tick(0.1);

            assertEquals(0, output.count("dispose"));
            assertEquals(2, output.applied.size());
        } finally {
            stage.hide();
        }
    }
}
