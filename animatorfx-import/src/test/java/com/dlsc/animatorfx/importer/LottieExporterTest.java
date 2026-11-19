package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.AudioLayer;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.Effect;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.Marker;
import com.dlsc.animatorfx.model.Mask;
import com.dlsc.animatorfx.model.MotionPath;
import com.dlsc.animatorfx.model.Paint;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.Rgba;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.SoundCue;
import com.dlsc.animatorfx.model.Transform;
import com.dlsc.animatorfx.model.Vec2;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.lottie4j.core.definition.LayerType;
import com.lottie4j.core.definition.MatteMode;
import com.lottie4j.core.file.LottieFileLoader;
import com.lottie4j.core.model.dot.DotLottie;
import com.lottie4j.core.model.keyframe.TimedKeyframe;
import com.lottie4j.core.model.shape.grouping.Group;
import com.lottie4j.core.model.shape.shape.Rectangle;
import com.lottie4j.core.model.shape.style.Fill;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxToolkit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The export to Lottie, checked three ways: what a document becomes in the Lottie4J model, that
 * the files are read back by Lottie4J, and that a document looks the same after it went out to
 * Lottie and came back through the importer.
 */
class LottieExporterTest {

    private static final double EPS = 1e-6;

    @TempDir
    Path directory;

    @BeforeAll
    static void startToolkit() throws Exception {
        FxToolkit.registerPrimaryStage();
    }

    private static AnimationFile file(Animation animation) {
        return new AnimationFile(animation, Map.of());
    }

    private static Animation document(Layer... layers) {
        return Animation.create("Export", 200, 100, 2).withFrameRate(30).withLayers(List.of(layers));
    }

    private static ShapeLayer rect(String name, double width, double height, Rgba color) {
        return new ShapeLayer(LayerBase.create(name, 2), new Geometry.Rect(Animatable.of(new Vec2(width, height)),
                Animatable.of(0.0)), Animatable.of(new Paint.Solid(color)), null, null);
    }

    private static <T> Keyframe<T> key(double time, T value, Easing easing) {
        return new Keyframe<>(UUID.randomUUID().toString(), time, value, easing);
    }

    private static List<com.lottie4j.core.model.layer.Layer> layers(Animation animation) throws IOException {
        return LottieExporter.convert(file(animation)).animation().layers();
    }

    @Test
    void theDocumentBecomesTheFrameOfTheAnimation() throws IOException {
        LottieExport export = LottieExporter.convert(file(document(rect("Box", 40, 20, Rgba.BLACK))
                .withBackground(new Rgba(1, 0.5, 0, 1)).withMarkers(List.of(new Marker("intro", 0.5, 1.5)))));

        var animation = export.animation();
        assertEquals("Export", animation.name());
        assertEquals(200, animation.width());
        assertEquals(100, animation.height());
        assertEquals(30, animation.framesPerSecond(), EPS);
        assertEquals(0, animation.inPoint(), EPS);
        assertEquals(60, animation.outPoint(), EPS);
        assertEquals("#ff8000", export.background());
        assertEquals("intro", animation.markers().get(0).name());
        assertEquals(15, animation.markers().get(0).time(), EPS);
        assertEquals(30, animation.markers().get(0).duration(), EPS);
        assertEquals(List.of(), export.warnings());
    }

    @Test
    void aRectangleIsPlacedByItsCentre() throws IOException {
        var layer = layers(document(rect("Box", 40, 20, new Rgba(1, 0, 0, 0.5)))).get(0);

        assertEquals(LayerType.SHAPE, layer.layerType());
        Group group = assertInstanceOf(Group.class, layer.shapes().get(0));
        Rectangle rectangle = assertInstanceOf(Rectangle.class, group.shapes().get(0));
        assertEquals(List.of(20.0, 10.0), numbers(rectangle.position()));
        assertEquals(List.of(40.0, 20.0), numbers(rectangle.size()));
        Fill fill = assertInstanceOf(Fill.class, group.shapes().get(1));
        // the alpha of the colour is the opacity of the fill
        assertEquals(List.of(50.0), numbers(fill.opacity()));
        assertEquals(List.of(1.0, 0.0, 0.0, 1.0), numbers(fill.color()));
    }

    private static List<Double> numbers(com.lottie4j.core.model.animation.Animated property) {
        return property.keyframes().stream().map(k -> ((Number) k).doubleValue()).toList();
    }

    @Test
    void keyframesKeepTheirTimeAndEasing() throws IOException {
        ShapeLayer box = rect("Box", 10, 10, Rgba.BLACK);
        Animatable<Vec2> position = Animatable.keyframed(List.of(
                key(0, new Vec2(0, 0), new Easing.CubicBezier(0.4, 0, 0.2, 1)),
                key(1, new Vec2(100, 0), Easing.HOLD),
                key(2, new Vec2(100, 50), Easing.LINEAR)));
        var layer = layers(document(box.withBase(box.base().withTransform(
                Transform.IDENTITY.withPosition(position))))).get(0);

        var keys = layer.transform().position().keyframes();
        assertEquals(3, keys.size());
        TimedKeyframe first = (TimedKeyframe) keys.get(0);
        assertEquals(0, first.time(), EPS);
        assertEquals(0.4, first.easingOut().x().get(0), EPS);
        assertEquals(0.0, first.easingOut().y().get(0), EPS);
        assertEquals(0.2, first.easingIn().x().get(0), EPS);
        assertEquals(1.0, first.easingIn().y().get(0), EPS);
        TimedKeyframe second = (TimedKeyframe) keys.get(1);
        assertEquals(30, second.time(), EPS);
        assertEquals(1, second.holdFrame());
        assertEquals(60, ((TimedKeyframe) keys.get(2)).time(), EPS);
    }

    @Test
    void aGroupBecomesANullLayerItsChildrenAreParentedTo() throws IOException {
        GroupLayer group = new GroupLayer(LayerBase.create("Group", 2).withTransform(Transform.at(30, 40))
                .withOpacity(Animatable.of(0.5)), List.of(rect("Below", 10, 10, Rgba.BLACK),
                rect("Above", 10, 10, Rgba.WHITE)));
        LottieExport export = LottieExporter.convert(file(document(group)));

        // top-most first, the carrier last
        var layers = export.animation().layers();
        assertEquals(List.of("Above", "Below", "Group"), layers.stream().map(l -> l.name()).toList());
        var carrier = layers.get(2);
        assertEquals(LayerType.NULL, carrier.layerType());
        assertEquals(List.of(30.0, 40.0), numbers(carrier.transform().position()));
        for (var child : layers.subList(0, 2)) {
            assertEquals(carrier.indexLayer(), child.indexParent());
            // the opacity does not travel along a parent
            assertEquals(List.of(50.0), numbers(child.transform().opacity()));
        }
        assertEquals(1, export.warnings().size());
        assertTrue(export.warnings().get(0).startsWith("The opacity of a group is applied to each of its layers"));
    }

    @Test
    void aMaskBecomesATrackMatteRightAboveTheLayer() throws IOException {
        ShapeLayer mask = rect("Mask", 50, 50, Rgba.WHITE);
        ShapeLayer masked = rect("Masked", 100, 100, Rgba.BLACK);
        masked = masked.withBase(masked.base().withMask(new Mask(mask.id(), Mask.Mode.ALPHA)));
        var layers = layers(document(mask, masked, rect("Other", 5, 5, Rgba.BLACK)));

        assertEquals(List.of("Other", "Mask", "Masked"), layers.stream().map(l -> l.name()).toList());
        assertEquals(1, layers.get(1).matteTarget());
        assertEquals(MatteMode.ALPHA, layers.get(2).matteMode());
        assertEquals(layers.get(1).indexLayer(), layers.get(2).tp());
        assertNull(layers.get(0).matteMode());
    }

    @Test
    void aGroupThatMasksBecomesAPrecomposition() throws IOException {
        GroupLayer mask = new GroupLayer(LayerBase.create("Mask", 2),
                List.of(rect("A", 10, 10, Rgba.WHITE), rect("B", 20, 20, Rgba.WHITE)));
        ShapeLayer masked = rect("Masked", 100, 100, Rgba.BLACK);
        masked = masked.withBase(masked.base().withMask(new Mask(mask.id(), Mask.Mode.ALPHA)));
        var animation = LottieExporter.convert(file(document(mask, masked))).animation();

        assertEquals(2, animation.layers().size());
        var matte = animation.layers().get(0);
        assertEquals(LayerType.PRECOMPOSITION, matte.layerType());
        assertEquals(1, matte.matteTarget());
        assertEquals(matte.indexLayer(), animation.layers().get(1).tp());
        var composition = animation.assets().get(0);
        assertEquals(matte.referenceId(), composition.id());
        assertEquals(List.of("B", "A"), composition.layers().stream().map(l -> l.name()).toList());
    }

    @Test
    void aMotionPathBecomesPositionKeyframes() throws IOException {
        PathData line = new PathData(List.of(new PathData.Contour(List.of(
                new PathData.Vertex(new Vec2(0, 0), Vec2.ZERO, Vec2.ZERO),
                new PathData.Vertex(new Vec2(100, 0), Vec2.ZERO, Vec2.ZERO)), false)));
        ShapeLayer box = rect("Box", 10, 10, Rgba.BLACK);
        Animatable<Double> progress = Animatable.keyframed(List.of(key(0, 0.0, Easing.LINEAR),
                key(1, 1.0, Easing.LINEAR)));
        LottieExport export = LottieExporter.convert(file(document(
                box.withBase(box.base().withMotionPath(new MotionPath(line, progress, false))))));

        var keys = export.animation().layers().get(0).transform().position().keyframes();
        assertEquals(31, keys.size());
        TimedKeyframe middle = (TimedKeyframe) keys.get(15);
        assertEquals(15, middle.time(), EPS);
        assertEquals(50, middle.values().get(0).doubleValue(), 1e-3);
        assertEquals(List.of("Motion paths are exported as one position keyframe per frame (1 layer)"),
                export.warnings());
    }

    @Test
    void whatLottieCannotHoldIsReported() throws IOException {
        ShapeLayer glowing = rect("Glowing", 10, 10, Rgba.BLACK);
        glowing = glowing.withBase(glowing.base().withEffects(List.of(new Effect.Glow(Animatable.of(0.5)),
                new Effect.Blur(Animatable.of(9.0)))));
        SlotLayer slot = new SlotLayer(LayerBase.create("Slot", 2), "content", 10, 10, Transform.IDENTITY);
        LottieExport export = LottieExporter.convert(file(document(glowing, slot)));

        assertEquals(1, export.animation().layers().size());
        var effects = export.animation().layers().get(0).effects();
        assertEquals(1, effects.size());
        // back through the factor the importer reads blurriness with
        assertEquals(10, ((Number) effects.get(0).values().get(0).value().keyframes().get(0)).doubleValue(), EPS);
        assertEquals(List.of("Glow has no counterpart in Lottie (1 effect skipped)",
                "Slots for JavaFX nodes have no counterpart in Lottie (1 layer skipped)"), export.warnings());
    }

    @Test
    void soundsAndCuesBecomeAudioLayersWithEmbeddedSound() throws IOException {
        Asset sound = new Asset("sound", Asset.Kind.AUDIO, "assets/sound.wav", "Ping");
        AudioLayer layer = AudioLayer.create("Music", "sound", 0.5, 1.5).withClipOffset(0.25)
                .withVolume(Animatable.of(0.8));
        Animation animation = document(layer).withAssets(List.of(sound))
                .withCues(List.of(SoundCue.create("Cue", 1, "sound")));
        LottieExport export = LottieExporter.convert(new AnimationFile(animation,
                Map.of("sound", new byte[]{0, 1, 2, 3})));

        var layers = export.animation().layers();
        assertEquals(List.of("Cue", "Music"), layers.stream().map(l -> l.name()).toList());
        var music = layers.get(1);
        assertEquals(LayerType.AUDIO, music.layerType());
        assertEquals(15, music.inPoint(), EPS);
        assertEquals(45, music.outPoint(), EPS);
        assertEquals(7.5, music.startTime(), EPS);
        assertEquals(List.of(80.0, 80.0), numbers(music.audioSettings().level()));
        assertEquals(30, layers.get(0).inPoint(), EPS);
        // both use the one sound of the document
        assertEquals(1, export.animation().assets().size());
        assertEquals("data:audio/wav;base64,AAECAw==", export.animation().assets().get(0).fileName());
        assertEquals(music.referenceId(), layers.get(0).referenceId());

        // and the importer takes it back as it was
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        LottieExporter.writeJson(new AnimationFile(animation, Map.of("sound", new byte[]{0, 1, 2, 3})), json);
        ImportResult back = LottieImporter.read(new ByteArrayInputStream(json.toByteArray()), "back");
        AudioLayer read = assertInstanceOf(AudioLayer.class, back.file().animation().layers().get(0));
        assertEquals(0.5, read.base().inPoint(), EPS);
        assertEquals(1.5, read.base().outPoint(), EPS);
        assertEquals(0.25, read.clipOffset(), EPS);
        assertEquals(0.8, read.volume().at(0), EPS);
    }

    @Test
    void theDotLottieFileIsReadByLottie4J() throws Exception {
        File target = directory.resolve("export.lottie").toFile();
        try (var out = Files.newOutputStream(target.toPath())) {
            assertEquals(List.of(), LottieExporter.writeDotLottie(
                    file(document(rect("Box", 40, 20, Rgba.BLACK)).withBackground(Rgba.WHITE)), out));
        }

        DotLottie read = LottieFileLoader.loadDotLottie(target);
        assertEquals("2", read.manifest().version());
        assertEquals("AnimatorFX", read.manifest().generator());
        assertEquals("#ffffff", read.manifest().animations().get(0).background());
        assertEquals(1, read.animations().size());
        assertEquals("Box", read.animations().get(0).layers().get(0).name());
    }

    /**
     * Out to Lottie and back in: the picture must be the one the document had. The fixtures were
     * imported from Lottie, so everything in them can be said in Lottie again; what remains are
     * edges that differ by a pixel.
     */
    @ParameterizedTest
    @ValueSource(strings = {"layers", "shapes", "transform", "parenting", "masks", "morph", "precomp"})
    void aDocumentLooksTheSameAfterExportAndImport(String name) throws Exception {
        ImportResult imported;
        try (var in = LottieExporterTest.class.getResourceAsStream("lottie/" + name + ".json")) {
            imported = LottieImporter.read(Objects.requireNonNull(in), name);
        }
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        List<String> warnings = LottieExporter.writeJson(imported.file(), json);
        ImportResult back = LottieImporter.read(new ByteArrayInputStream(json.toByteArray()), name);
        assertNotNull(back.file());
        assertFalse(back.file().animation().layers().isEmpty());

        Animation animation = imported.file().animation();
        double worst = 0;
        for (int i = 0; i <= 6; i++) {
            double time = (animation.duration() - 1 / animation.frameRate()) * i / 6;
            double share = LottieOracleTest.onFxThread(() -> {
                WritableImage before = LottieOracleTest.ours(imported.file(), time);
                WritableImage after = LottieOracleTest.ours(back.file(), time);
                return LottieOracleTest.difference(before, after);
            });
            worst = Math.max(worst, share);
        }
        System.out.printf("LOTTIE-EXPORT %s worst %.2f%% %s%n", name, worst * 100, warnings);
        assertTrue(worst <= 0.01, name + ": " + worst * 100 + "% of the pixels differ after export and import");
    }

    /**
     * The exported file as the Lottie4J player draws it, against the document as our player
     * draws it. The limits are those of {@link LottieOracleTest}: the two renderers differ by
     * what is written down there, and the export must not add to it.
     */
    @ParameterizedTest
    @ValueSource(strings = {"transform", "parenting", "masks", "morph", "precomp"})
    void theExportedFileMatchesTheDocumentInTheReferenceRenderer(String name) throws Exception {
        ImportResult imported;
        try (var in = LottieExporterTest.class.getResourceAsStream("lottie/" + name + ".json")) {
            imported = LottieImporter.read(Objects.requireNonNull(in), name);
        }
        var exported = LottieExporter.convert(imported.file()).animation();
        Animation animation = imported.file().animation();
        double worst = 0;
        for (int i = 0; i <= 6; i++) {
            double time = (animation.duration() - 1 / animation.frameRate()) * i / 6;
            double frame = time * animation.frameRate();
            double share = LottieOracleTest.onFxThread(() -> LottieOracleTest.difference(
                    LottieOracleTest.ours(imported.file(), time), LottieOracleTest.reference(exported, frame)));
            worst = Math.max(worst, share);
        }
        System.out.printf("LOTTIE-EXPORT-ORACLE %s worst %.2f%%%n", name, worst * 100);
        assertTrue(worst <= 0.05, name + ": " + worst * 100 + "% of the pixels differ in the Lottie4J player");
    }
}
