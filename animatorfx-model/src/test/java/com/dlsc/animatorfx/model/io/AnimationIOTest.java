package com.dlsc.animatorfx.model.io;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.Easing;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.ShapeLayer;
import com.dlsc.animatorfx.model.SlotLayer;
import com.dlsc.animatorfx.model.Vec2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationIOTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0, 1, 2, 3, (byte) 0xFF};
    private static final byte[] TTF = new byte[5000];

    static {
        for (int i = 0; i < TTF.length; i++) {
            TTF[i] = (byte) (i * 31);
        }
    }

    private static final String MINIMAL =
            "{\"formatVersion\":1,\"width\":100,\"height\":100,\"duration\":1,\"layers\":[]}";
    private static final String WITH_ASSET = "{\"formatVersion\":1,\"width\":100,\"height\":100,\"duration\":1,"
            + "\"assets\":[{\"id\":\"a\",\"kind\":\"image\",\"path\":\"assets/a.png\"}]}";

    private static AnimationFile kitchenSink() {
        return new AnimationFile(KitchenSink.animation(), Map.of("img-1", PNG, "font-1", TTF));
    }

    private static byte[] bytes(AnimationFile file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AnimationIO.write(file, out);
        return out.toByteArray();
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    /** A zip with the given entry names and contents, alternating. */
    private static byte[] zip(Object... namesAndContents) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                zip.putNextEntry(new ZipEntry((String) namesAndContents[i]));
                Object content = namesAndContents[i + 1];
                zip.write(content instanceof byte[] b ? b : ((String) content).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static List<String> entryNames(byte[] zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    @Test
    void zipRoundTripKeepsDocumentAndAssetBytes() throws Exception {
        AnimationFile file = kitchenSink();

        AnimationFile back = AnimationIO.read(new ByteArrayInputStream(bytes(file)));

        assertEquals(file.animation(), back.animation());
        assertEquals(file.assets().keySet(), back.assets().keySet());
        assertArrayEquals(PNG, back.assets().get("img-1"));
        assertArrayEquals(TTF, back.assets().get("font-1"));
    }

    @Test
    void writesAnimationJsonFirstThenAssetsInDocumentOrder() throws Exception {
        byte[] zip = bytes(kitchenSink());

        assertEquals(List.of("animation.json", "assets/img-1.png", "assets/font-1.ttf"), entryNames(zip));
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            in.getNextEntry();
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(JsonWriter.write(AnimationJson.toJson(KitchenSink.animation())) + "\n", json);
        }
    }

    @Test
    void identicalDocumentsProduceIdenticalBytes() throws Exception {
        Map<String, byte[]> reordered = new LinkedHashMap<>();
        reordered.put("unused", new byte[]{1});
        reordered.put("font-1", TTF.clone());
        reordered.put("img-1", PNG.clone());

        assertArrayEquals(bytes(kitchenSink()), bytes(new AnimationFile(KitchenSink.animation(), reordered)));
    }

    @Test
    void bytesDoNotDependOnTheTimeZone() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            byte[] utc = bytes(kitchenSink());
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            assertArrayEquals(utc, bytes(kitchenSink()));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void readsBareJson() throws Exception {
        AnimationFile file = AnimationIO.read(stream(MINIMAL));

        assertEquals(Animation.create("Untitled", 100, 100, 1), file.animation());
        assertTrue(file.assets().isEmpty());
    }

    @Test
    void readsBareJsonWithByteOrderMarkAndLeadingWhitespace() throws Exception {
        assertEquals(Animation.create("Untitled", 100, 100, 1), AnimationIO.read(stream("\uFEFF\n " + MINIMAL)).animation());
    }

    @Test
    void bareJsonThatReferencesAssetsIsRejected() {
        AnimationFormatException e = assertThrows(AnimationFormatException.class,
                () -> AnimationIO.read(stream(WITH_ASSET)));

        assertEquals("assets[0].path", e.jsonPath());
    }

    @Test
    void missingAssetEntryIsRejected() throws Exception {
        byte[] zip = zip("animation.json", WITH_ASSET, "assets/other.png", PNG);

        AnimationFormatException e = assertThrows(AnimationFormatException.class,
                () -> AnimationIO.read(new ByteArrayInputStream(zip)));

        assertEquals("assets[0].path", e.jsonPath());
        assertTrue(e.getMessage().contains("assets/a.png"), e.getMessage());
    }

    @Test
    void unknownEntriesAreIgnored() throws Exception {
        byte[] zip = zip("thumbnail.png", PNG, "assets/", "", "assets/a.png", PNG, "animation.json", WITH_ASSET,
                "notes/readme.txt", "hello");

        AnimationFile file = AnimationIO.read(new ByteArrayInputStream(zip));

        assertEquals(List.of(new Asset("a", Asset.Kind.IMAGE, "assets/a.png", "")), file.animation().assets());
        assertEquals(1, file.assets().size());
        assertArrayEquals(PNG, file.assets().get("a"));
    }

    @Test
    void unsafeEntryNamesAreRejected() throws Exception {
        for (String name : List.of("../evil.png", "assets/../../evil.png", "/etc/passwd", "assets\\..\\evil",
                "C:\\evil", "\\evil")) {
            byte[] zip = zip("animation.json", MINIMAL, name, PNG);
            assertThrows(AnimationFormatException.class, () -> AnimationIO.read(new ByteArrayInputStream(zip)), name);
        }
    }

    @Test
    void entryNamesWithDotsInsideASegmentAreFine() throws Exception {
        byte[] zip = zip("animation.json", MINIMAL, "assets/a..b.png", PNG);

        assertNotNull(AnimationIO.read(new ByteArrayInputStream(zip)));
    }

    @Test
    void archiveWithoutAnimationJsonIsRejected() throws Exception {
        byte[] zip = zip("assets/a.png", PNG);

        assertThrows(AnimationFormatException.class, () -> AnimationIO.read(new ByteArrayInputStream(zip)));
        assertThrows(AnimationFormatException.class,
                () -> AnimationIO.read(new ByteArrayInputStream(new byte[]{'P', 'K', 3, 4, 9, 9, 9})));
    }

    @Test
    void malformedInputIsAFormatException() {
        assertThrows(AnimationFormatException.class, () -> AnimationIO.read(new ByteArrayInputStream(new byte[0])));
        assertThrows(AnimationFormatException.class, () -> AnimationIO.read(stream("{")));
        assertThrows(AnimationFormatException.class, () -> AnimationIO.read(stream("P")));
        assertThrows(AnimationFormatException.class,
                () -> AnimationIO.read(new ByteArrayInputStream(new byte[]{'"', (byte) 0xC3, '"'})));
        AnimationFormatException e = assertThrows(AnimationFormatException.class,
                () -> AnimationIO.read(stream("{\"formatVersion\":1,\"width\":1,\"height\":1,\"duration\":1,"
                        + "\"layers\":[{\"type\":\"shape\",\"geometry\":{\"type\":\"polygon\",\"sides\":1,\"radius\":1}}]}")));
        assertEquals("layers[0].geometry.sides", e.jsonPath());
    }

    @Test
    void readDoesNotCloseTheStreamAndCopesWithSlowStreams() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream trickle = new FilterInputStream(new ByteArrayInputStream(bytes(kitchenSink()))) {
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return super.read(b, off, Math.min(len, 1));
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };

        assertEquals(KitchenSink.animation(), AnimationIO.read(trickle).animation());
        assertFalse(closed.get());
    }

    @Test
    void writeDoesNotCloseTheStream() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        AnimationIO.write(kitchenSink(), new FilterOutputStream(bytes) {
            @Override
            public void close() {
                closed.set(true);
            }
        });

        assertFalse(closed.get());
        assertArrayEquals(bytes(kitchenSink()), bytes.toByteArray());
    }

    @Test
    void writingADocumentWhoseAssetHasNoBytesFails() {
        AnimationFile file = new AnimationFile(KitchenSink.animation(), Map.of("img-1", PNG));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AnimationIO.write(file, out));

        assertTrue(e.getMessage().contains("font-1"), e.getMessage());
        assertEquals(0, out.size());
    }

    @Test
    void writingUnsafeOrClashingAssetPathsFails() {
        for (String path : List.of("../a.png", "/a.png", "animation.json", "")) {
            Animation a = Animation.create("a", 1, 1, 1)
                    .withAssets(List.of(new Asset("a", Asset.Kind.IMAGE, path, "")));
            assertThrows(IllegalArgumentException.class,
                    () -> AnimationIO.write(new AnimationFile(a, Map.of("a", PNG)), new ByteArrayOutputStream()), path);
        }
        Animation twice = Animation.create("a", 1, 1, 1).withAssets(List.of(
                new Asset("a", Asset.Kind.IMAGE, "assets/x.png", ""), new Asset("b", Asset.Kind.IMAGE, "assets/x.png", "")));
        assertThrows(IllegalArgumentException.class, () -> AnimationIO.write(
                new AnimationFile(twice, Map.of("a", PNG, "b", PNG)), new ByteArrayOutputStream()));
    }

    @Test
    void writesAndReadsAPath(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("sink.afx");
        Files.writeString(target, "previous content");

        AnimationIO.write(kitchenSink(), target);

        assertArrayEquals(bytes(kitchenSink()), Files.readAllBytes(target));
        AnimationFile back = AnimationIO.read(target);
        assertEquals(KitchenSink.animation(), back.animation());
        assertArrayEquals(TTF, back.assets().get("font-1"));
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of(target), files.toList());
        }
    }

    @Test
    void failedWriteLeavesTheTargetUntouched(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("sink.afx");
        Files.writeString(target, "previous content");
        AnimationFile incomplete = new AnimationFile(KitchenSink.animation(), Map.of());

        assertThrows(IllegalArgumentException.class, () -> AnimationIO.write(incomplete, target));

        assertEquals("previous content", Files.readString(target));
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of(target), files.toList());
        }
    }

    @Test
    void readsFixturesFromUrls() throws Exception {
        URL minimal = AnimationIOTest.class.getResource("minimal.json");
        assertEquals(Animation.create("Untitled", 100, 100, 1), AnimationIO.read(minimal).animation());

        Animation a = AnimationIO.read(AnimationIOTest.class.getResource("handwritten.json")).animation();
        assertEquals("Bouncing dot", a.name());
        assertEquals(1, a.markers().size());
        ShapeLayer dot = (ShapeLayer) a.layers().get(0);
        assertEquals("dot", dot.name());
        var position = dot.base().transform().position();
        assertEquals(3, position.keyframes().size());
        assertEquals(new Easing.CubicBezier(0.5, 0, 1, 1), position.keyframes().get(0).easing());
        assertEquals(Easing.HOLD, position.keyframes().get(1).easing());
        assertEquals(new Vec2(20, 100), position.at(1.5));
        SlotLayer slot = (SlotLayer) ((GroupLayer) a.layers().get(1)).children().get(0);
        assertEquals("button", slot.slotName());
    }

    @Test
    void assetMapIsCopied() {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        assets.put("a", PNG);
        AnimationFile file = new AnimationFile(Animation.create("a", 1, 1, 1), assets);

        assets.clear();

        assertEquals(1, file.assets().size());
        assertThrows(UnsupportedOperationException.class, () -> file.assets().clear());
    }
}
