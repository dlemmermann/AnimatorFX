package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.SlotLayer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whatever a fixture contains, the imported document must be one the model accepts: it saves,
 * reads back unchanged and evaluates at any time.
 */
class FixturesTest {

    private static List<Path> fixtures(String directory, String extension) throws IOException, URISyntaxException {
        Path path = Path.of(FixturesTest.class.getResource(directory).toURI());
        try (Stream<Path> files = Files.list(path)) {
            List<Path> result = files.filter(file -> file.toString().endsWith(extension)).sorted().toList();
            assertFalse(result.isEmpty());
            return result;
        }
    }

    @TestFactory
    Stream<DynamicTest> everySvgFixtureGivesAValidDocument() throws Exception {
        return fixtures("svg", ".svg").stream().map(file -> DynamicTest.dynamicTest(file.getFileName().toString(),
                () -> {
                    try (InputStream in = Files.newInputStream(file)) {
                        check(SvgImporter.read(in, "fixture"));
                    }
                }));
    }

    @TestFactory
    Stream<DynamicTest> everyLottieFixtureGivesAValidDocument() throws Exception {
        return fixtures("lottie", ".json").stream().map(file -> DynamicTest.dynamicTest(file.getFileName().toString(),
                () -> {
                    try (InputStream in = Files.newInputStream(file)) {
                        check(LottieImporter.read(in, "fixture"));
                    }
                }));
    }

    private static void check(ImportResult result) throws IOException {
        ImportChecks.assertSavesAndRenders(result.file());
        Animation animation = result.file().animation();
        Set<String> ids = new HashSet<>();
        Set<String> slots = new HashSet<>();
        for (Layer layer : Layers.flatten(animation)) {
            assertTrue(ids.add(layer.id()), "duplicate layer id of " + layer.name());
            assertTrue(layer.base().inPoint() >= 0 && layer.base().outPoint() <= animation.duration() + 1e-9
                    && layer.base().inPoint() <= layer.base().outPoint(), "time range of " + layer.name());
            if (layer instanceof SlotLayer slot) {
                assertTrue(slots.add(slot.slotName()));
            }
        }
        assertEquals(new HashSet<>(result.warnings()).size(), result.warnings().size(), "warnings are not repeated");
    }
}
