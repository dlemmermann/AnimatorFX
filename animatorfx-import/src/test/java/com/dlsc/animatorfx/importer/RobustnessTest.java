package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Geometry;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.Mask;
import com.dlsc.animatorfx.model.PathData;
import com.dlsc.animatorfx.model.ShapeLayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.dlsc.animatorfx.importer.ImportChecks.assertVec;
import static com.dlsc.animatorfx.importer.ImportChecks.named;
import static com.dlsc.animatorfx.importer.ImportChecks.names;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hostile and broken input: whatever is imported must save and read back, and nothing but an
 * {@link IOException} may leave an importer.
 */
@Timeout(20)
class RobustnessTest {

    private static final String SVG = "<svg xmlns='http://www.w3.org/2000/svg' width='100' height='100'>";
    private static final String STOPPED = "Import stopped at 20000 layers; the rest of the file was skipped";
    private static final String TOO_DEEP = "Nesting deeper than 64 levels is not supported; the deeper content was skipped";

    private static ImportResult svg(String svg) throws IOException {
        ImportResult result = SvgImporter.read(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)), "test");
        ImportChecks.assertSavesAndRenders(result.file());
        return result;
    }

    private static ImportResult lottie(String json) throws IOException {
        ImportResult result = LottieImporter.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                "test");
        ImportChecks.assertSavesAndRenders(result.file());
        return result;
    }

    private static String lottieDocument(String assets, String layers) {
        return "{\"fr\":30,\"ip\":0,\"op\":60,\"w\":100,\"h\":100,\"assets\":[" + assets + "],\"layers\":[" + layers
                + "]}";
    }

    private static final String BOX = "\"shapes\":[{\"ty\":\"rc\",\"p\":{\"a\":0,\"k\":[0,0]},\"s\":{\"a\":0,\"k\":[10,10]}},"
            + "{\"ty\":\"fl\",\"c\":{\"a\":0,\"k\":[1,0,0,1]},\"o\":{\"a\":0,\"k\":100}}]";

    private static void assertWarns(ImportResult result, String start) {
        assertTrue(result.warnings().stream().anyMatch(line -> line.startsWith(start)),
                "expected a warning starting with \"" + start + "\" in " + result.warnings());
    }

    // ------------------------------------------------------------------ numbers that are not finite

    @Test
    void aPathEndsAtACoordinateThatIsNotFinite() throws IOException {
        ImportResult result = svg(SVG + "<path id='p' d='M0 0 L10 0 L1e999 5 L20 20'/></svg>");
        PathData path = ((Geometry.PathGeometry) named(result.file().animation(), "p", ShapeLayer.class).geometry())
                .path().at(0);
        // SVG error handling: what was read before the bad number stays
        assertEquals(2, path.contours().get(0).vertices().size());
        assertWarns(result, "Path data was cut off at a number that is not finite or too large");

        // a path that loses everything draws nothing
        result = svg(SVG + "<path d='M0 0 L1e999 5'/></svg>");
        assertEquals(List.of(), result.file().animation().layers());
        assertWarns(result, "Path data was cut off");
    }

    @Test
    void anArcWithAbsurdRadiiEndsThePath() throws IOException {
        ImportResult result = svg(SVG + "<path id='p' d='M0 0 L5 5 A1e200 1e200 0 0 1 10 10'/></svg>");
        PathData path = ((Geometry.PathGeometry) named(result.file().animation(), "p", ShapeLayer.class).geometry())
                .path().at(0);
        assertEquals(2, path.contours().get(0).vertices().size());
        assertWarns(result, "Path data was cut off");
    }

    @Test
    void anAttributeThatIsNotFiniteTakesItsDefault() throws IOException {
        ImportResult result = svg(SVG + "<rect id='r' x='1e999' y='7' width='10' height='10'/></svg>");
        assertVec(0, 7, named(result.file().animation(), "r").base().transform().position().at(0));
        assertWarns(result, "Lengths that are not finite or too large use their default");
    }

    @Test
    void aDocumentSizeThatIsNotFiniteFallsBack() throws IOException {
        ImportResult result = SvgImporter.read(new ByteArrayInputStream(
                "<svg xmlns='http://www.w3.org/2000/svg' width='1e999' height='1e400' viewBox='0 0 40 20'/>"
                        .getBytes(StandardCharsets.UTF_8)), "test");
        ImportChecks.assertSavesAndRenders(result.file());
        assertEquals(40, result.file().animation().width());
        assertEquals(20, result.file().animation().height());
        assertWarns(result, "Lengths that are not finite or too large use their default");

        result = svg("<svg xmlns='http://www.w3.org/2000/svg' width='1e999'/>");
        assertEquals(300, result.file().animation().width());
    }

    @Test
    void anElementWithATransformThatIsNotFiniteIsDropped() throws IOException {
        ImportResult result = svg(SVG + "<rect id='gone' width='10' height='10' transform='scale(1e200) scale(1e200)'/>"
                + "<g id='deep' transform='scale(1e999)'><rect width='1' height='1'/></g>"
                + "<rect id='kept' width='10' height='10'/></svg>");
        assertEquals(List.of("kept"), names(result.file().animation().layers()));
        assertWarns(result, "Elements whose transform is not finite or too large were dropped (2 elements)");
    }

    @Test
    void nonAsciiDigitsAreNotNumbers() throws IOException {
        // like any other text that is not a length: the attribute has its default, here no width
        ImportResult result = svg(SVG + "<rect width='\u0663' height='5'/><rect id='ok' width='3' height='5' "
                + "transform='translate(\u0663)' x='\u0661e\u0662'/></svg>");
        assertEquals(List.of("ok"), names(result.file().animation().layers()));
        assertWarns(result, "Transforms that cannot be parsed are ignored");
    }

    @Test
    void aLottieDocumentThatCannotBeTimedIsRejected() {
        for (String header : List.of("\"fr\":1e-300,\"ip\":0,\"op\":1e300", "\"fr\":30,\"ip\":0,\"op\":1e300",
                "\"fr\":30,\"ip\":-1e300,\"op\":60", "\"fr\":5000,\"ip\":0,\"op\":60", "\"fr\":30,\"ip\":0,\"op\":1e9",
                "\"fr\":0.5,\"ip\":0,\"op\":60")) {
            IOException failure = assertThrows(IOException.class,
                    () -> lottie("{" + header + ",\"w\":100,\"h\":100,\"layers\":[]}"), header);
            assertTrue(failure.getMessage().contains("Not a valid Lottie document"), failure.getMessage());
        }
        assertThrows(IOException.class, () -> lottie("{\"fr\":30,\"ip\":0,\"op\":60,\"w\":1e300,\"h\":100,\"layers\":[]}"));
        assertThrows(IOException.class, () -> lottie("{\"fr\":30,\"ip\":0,\"op\":60,\"w\":100,\"h\":1e999,\"layers\":[]}"));
    }

    @Test
    void lottieValuesThatAreAbsurdlyLargeTakeTheirDefault() throws IOException {
        String shapes = "\"shapes\":[{\"ty\":\"el\",\"nm\":\"Big\",\"p\":{\"a\":0,\"k\":[1e308,1e308]},"
                + "\"s\":{\"a\":1,\"k\":[{\"t\":0,\"s\":[1.7e308,1.7e308]},{\"t\":30,\"s\":[10,10]}]}},"
                + "{\"ty\":\"gf\",\"t\":2,\"s\":{\"a\":0,\"k\":[-1e308,-1e308]},\"e\":{\"a\":0,\"k\":[1e308,1e308]},"
                + "\"o\":{\"a\":0,\"k\":100},\"g\":{\"p\":2,\"k\":{\"a\":0,\"k\":[0,1,1,1,1,0,0,0]}}}]";
        ImportResult result = lottie(lottieDocument("", "{\"ty\":4,\"nm\":\"L\",\"ip\":0,\"op\":60,"
                + "\"ks\":{\"p\":{\"a\":0,\"k\":[1e300,5]},\"r\":{\"a\":1,\"k\":[{\"t\":0,\"s\":[0]},{\"t\":1e300,\"s\":[90]}]}},"
                + shapes + "}"));
        Animation animation = result.file().animation();
        Layer layer = named(animation, "L");
        assertVec(0, 0, layer.base().transform().position().at(0));
        assertFalse(layer.base().transform().rotation().isAnimated());
        assertNotNull(named(animation, "Big"));
        assertWarns(result, "Properties with values that are not finite or too large use their default");
    }

    @Test
    void opacityStaysWithinItsRange() throws IOException {
        ImportResult result = lottie(lottieDocument("", "{\"ty\":4,\"nm\":\"L\",\"ip\":0,\"op\":60,"
                + "\"ks\":{\"o\":{\"a\":1,\"k\":[{\"t\":0,\"s\":[250]},{\"t\":30,\"s\":[-20]}]}},"
                + "\"masksProperties\":[{\"mode\":\"a\",\"o\":{\"a\":0,\"k\":300},\"pt\":{\"a\":0,\"k\":{\"c\":true,"
                + "\"v\":[[0,0],[5,0],[5,5]],\"i\":[[0,0],[0,0],[0,0]],\"o\":[[0,0],[0,0],[0,0]]}}}],"
                + "\"shapes\":[{\"ty\":\"gr\",\"nm\":\"G\",\"it\":[{\"ty\":\"rc\",\"s\":{\"a\":0,\"k\":[4,4]}},"
                + "{\"ty\":\"rc\",\"s\":{\"a\":0,\"k\":[2,2]}},{\"ty\":\"fl\",\"c\":{\"a\":0,\"k\":[1,0,0]}},"
                + "{\"ty\":\"tr\",\"o\":{\"a\":0,\"k\":900}}]}]}"));
        Animation animation = result.file().animation();
        assertEquals(1, named(animation, "L").base().opacity().at(0));
        assertEquals(0, named(animation, "L").base().opacity().at(1));
        assertEquals(1, named(animation, "G").base().opacity().at(0));
        // a mask at full opacity clips
        assertEquals(Mask.Mode.CLIP, named(animation, "L").base().mask().mode());
    }

    // ------------------------------------------------------------------ track mattes

    @Test
    void chainedTrackMattesStayBesideTheirSources() throws IOException {
        ImportResult result = lottie(lottieDocument("",
                "{\"ty\":4,\"ind\":1,\"nm\":\"A\",\"td\":1,\"ip\":0,\"op\":60," + BOX + "},"
                        + "{\"ty\":4,\"ind\":2,\"nm\":\"B\",\"tt\":1,\"ip\":0,\"op\":60," + BOX + "},"
                        + "{\"ty\":4,\"ind\":3,\"nm\":\"C\",\"tt\":1,\"ip\":0,\"op\":60," + BOX + "}"));
        Animation animation = result.file().animation();
        assertEquals(List.of("C", "B", "A"), names(animation.layers()));
        assertEquals(new Mask(named(animation, "A").id(), Mask.Mode.ALPHA), named(animation, "B").base().mask());
        assertEquals(new Mask(named(animation, "B").id(), Mask.Mode.ALPHA), named(animation, "C").base().mask());
    }

    @Test
    void trackMattesThatReferToEachOtherAreDeclined() throws IOException {
        ImportResult result = lottie(lottieDocument("",
                "{\"ty\":4,\"ind\":1,\"nm\":\"A\",\"tt\":1,\"tp\":2,\"ip\":0,\"op\":60," + BOX + "},"
                        + "{\"ty\":4,\"ind\":2,\"nm\":\"B\",\"tt\":1,\"tp\":1,\"ip\":0,\"op\":60," + BOX + "}"));
        Animation animation = result.file().animation();
        assertEquals(List.of("B", "A"), names(animation.layers()));
        // the first matte holds, the one that would close the circle does not
        assertEquals(new Mask(named(animation, "B").id(), Mask.Mode.ALPHA), named(animation, "A").base().mask());
        assertNull(named(animation, "B").base().mask());
        assertWarns(result, "Track mattes that refer to each other are not supported (1 matte ignored)");
    }

    @Test
    void aMatteChainThroughMaskedLayersKeepsEveryMaskBesideItsLayer() throws IOException {
        String mask = "\"masksProperties\":[{\"mode\":\"a\",\"o\":{\"a\":0,\"k\":100},\"pt\":{\"a\":0,\"k\":{\"c\":true,"
                + "\"v\":[[0,0],[5,0],[5,5]],\"i\":[[0,0],[0,0],[0,0]],\"o\":[[0,0],[0,0],[0,0]]}}}],";
        ImportResult result = lottie(lottieDocument("",
                "{\"ty\":4,\"ind\":1,\"nm\":\"A\",\"td\":1,\"ip\":0,\"op\":60," + mask + BOX + "},"
                        + "{\"ty\":4,\"ind\":2,\"nm\":\"B\",\"tt\":1,\"ip\":0,\"op\":60," + mask + BOX + "},"
                        + "{\"ty\":4,\"ind\":3,\"nm\":\"C\",\"tt\":1,\"parent\":1,\"ip\":0,\"op\":60," + mask + BOX + "},"
                        + "{\"ty\":4,\"ind\":4,\"nm\":\"D\",\"tt\":1,\"tp\":3,\"ip\":0,\"op\":60," + BOX + "}"));
        assertEquals(4, result.file().animation().layers().size());
    }

    // ------------------------------------------------------------------ expansion

    @Test
    void anSvgThatExpandsExponentiallyStopsAtTheLayerLimit() throws IOException {
        StringBuilder svg = new StringBuilder(SVG).append("<defs><rect id='l0' width='1' height='1'/>");
        for (int level = 1; level <= 9; level++) {
            svg.append("<g id='l").append(level).append("'>");
            for (int i = 0; i < 10; i++) {
                svg.append("<use href='#l").append(level - 1).append("'/>");
            }
            svg.append("</g>");
        }
        svg.append("</defs><use href='#l9'/><rect id='after' width='5' height='5'/></svg>");
        ImportResult result = svg(svg.toString());
        int layers = ImportChecks.measure(result.file().animation())[0];
        assertTrue(layers > 1000 && layers <= 20000, "layers: " + layers);
        assertEquals(1, result.warnings().stream().filter(STOPPED::equals).count(), result.warnings().toString());
    }

    @Test
    void aLongChainOfUseElementsStopsAtTheDepthLimit() throws IOException {
        StringBuilder svg = new StringBuilder(SVG);
        for (int i = 0; i < 20000; i++) {
            svg.append("<use id='u").append(i).append("' href='#u").append(i + 1).append("'/>");
        }
        svg.append("<rect id='u20000' width='1' height='1'/></svg>");
        ImportResult result = svg(svg.toString());
        assertEquals(1, result.warnings().stream().filter(TOO_DEEP::equals).count(), result.warnings().toString());
        assertTrue(depth(result.file().animation()) <= 64);
    }

    @Test
    void deeplyNestedGroupsStopAtTheDepthLimit() throws IOException {
        ImportResult result = svg(SVG + "<g>".repeat(90) + "<rect width='1' height='1'/>" + "</g>".repeat(90)
                + "</svg>");
        assertTrue(depth(result.file().animation()) <= 64);
        assertWarns(result, TOO_DEEP);
    }

    private static int depth(Animation animation) {
        return ImportChecks.measure(animation)[1];
    }

    @Test
    void aLottieThatExpandsExponentiallyStopsAtTheLayerLimit() throws IOException {
        StringBuilder assets = new StringBuilder("{\"id\":\"c0\",\"layers\":[{\"ty\":4,\"ip\":0,\"op\":60," + BOX + "}]}");
        for (int level = 1; level <= 9; level++) {
            assets.append(",{\"id\":\"c").append(level).append("\",\"layers\":[");
            for (int i = 0; i < 10; i++) {
                assets.append(i == 0 ? "" : ",").append("{\"ty\":0,\"ip\":0,\"op\":60,\"refId\":\"c").append(level - 1)
                        .append("\"}");
            }
            assets.append("]}");
        }
        ImportResult result = lottie(lottieDocument(assets.toString(), "{\"ty\":0,\"ip\":0,\"op\":60,\"refId\":\"c9\"}"));
        int layers = ImportChecks.measure(result.file().animation())[0];
        assertTrue(layers > 1000 && layers <= 20000, "layers: " + layers);
        assertEquals(1, result.warnings().stream().filter(STOPPED::equals).count(), result.warnings().toString());
    }

    @Test
    void aLongLottieParentChainIsTreatedAsUnparented() throws IOException {
        StringBuilder layers = new StringBuilder();
        for (int i = 1; i <= 2000; i++) {
            layers.append(i == 1 ? "" : ",").append("{\"ty\":3,\"ind\":").append(i).append(",\"ip\":0,\"op\":60")
                    .append(i < 2000 ? ",\"parent\":" + (i + 1) : "").append("}");
        }
        ImportResult result = lottie(lottieDocument("", layers.toString()));
        int count = ImportChecks.measure(result.file().animation())[0];
        assertTrue(count <= 20000, "layers: " + count);
        assertTrue(depth(result.file().animation()) <= 64);
        assertWarns(result, "Parent chains longer than 64 layers are not supported; the layers are treated as unparented");
    }

    @Test
    void theNumberOfStarPointsIsLimited() throws IOException {
        String star = "{\"ty\":\"sr\",\"nm\":\"Star\",\"sy\":%d,\"pt\":{\"a\":0,\"k\":%s},"
                + "\"p\":{\"a\":1,\"k\":[{\"t\":0,\"s\":[0,0]},{\"t\":30,\"s\":[10,10]}]},\"or\":{\"a\":0,\"k\":20},"
                + "\"ir\":{\"a\":0,\"k\":10}},{\"ty\":\"gf\",\"t\":1,\"s\":{\"a\":0,\"k\":[0,0]},\"e\":{\"a\":0,\"k\":[10,0]},"
                + "\"o\":{\"a\":0,\"k\":100},\"g\":{\"p\":2,\"k\":{\"a\":0,\"k\":[0,1,1,1,1,0,0,0]}}}";
        ImportResult result = lottie(lottieDocument("", "{\"ty\":4,\"nm\":\"L\",\"ip\":0,\"op\":60,\"shapes\":["
                + String.format(star, 1, "1e9") + "]}"));
        PathData path = assertInstanceOf(Geometry.PathGeometry.class,
                named(result.file().animation(), "Star", ShapeLayer.class).geometry()).path().at(0);
        assertEquals(2000, path.contours().get(0).vertices().size());
        assertWarns(result, "Stars and polygons have at most 1000 points");

        result = lottie(lottieDocument("", "{\"ty\":4,\"nm\":\"L\",\"ip\":0,\"op\":60,\"shapes\":["
                + String.format(star, 2, "5000") + "]}"));
        assertWarns(result, "Stars and polygons have at most 1000 points");

        // a count beyond any number a document holds is no count at all: the default of five
        result = lottie(lottieDocument("", "{\"ty\":4,\"nm\":\"L\",\"ip\":0,\"op\":60,\"shapes\":["
                + String.format(star, 2, "1e300") + "]}"));
        assertWarns(result, "Properties with values that are not finite or too large use their default");
    }

    // ------------------------------------------------------------------ limits on the result, whatever made the layers

    private static final String MASK = "\"masksProperties\":[{\"mode\":\"a\",\"o\":{\"a\":0,\"k\":100},\"pt\":{\"a\":0,"
            + "\"k\":{\"c\":true,\"v\":[[0,0],[5,0],[5,5]],\"i\":[[0,0],[0,0],[0,0]],\"o\":[[0,0],[0,0],[0,0]]}}}],";

    @Test
    void precompositionsUnderParentChainsDoNotOverflowTheStack() throws IOException {
        // 63 precompositions inside each other, each parented to a chain of 64 layers that are not
        // converted themselves: the carriers alone would nest more than 4000 groups
        StringBuilder assets = new StringBuilder();
        for (int level = 1; level <= 63; level++) {
            assets.append(level == 1 ? "" : ",").append("{\"id\":\"c").append(level).append("\",\"layers\":[");
            assets.append(level < 63
                    ? "{\"ty\":0,\"ind\":1,\"parent\":2,\"ip\":0,\"op\":60,\"refId\":\"c" + (level + 1) + "\"}"
                    : "{\"ty\":4,\"ind\":1,\"parent\":2,\"ip\":0,\"op\":60," + BOX + "}");
            for (int ind = 2; ind <= 65; ind++) {
                assets.append(",{\"ty\":13,\"ind\":").append(ind).append(ind < 65 ? ",\"parent\":" + (ind + 1) : "")
                        .append("}");
            }
            assets.append("]}");
        }
        String json = lottieDocument(assets.toString(), "{\"ty\":0,\"ip\":0,\"op\":60,\"refId\":\"c1\"}");
        ImportResult result = ImportChecks.onSmallStack(() -> LottieImporter.read(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test"));
        ImportChecks.assertSavesAndRenders(result.file());
        assertEquals(1, result.warnings().stream().filter(TOO_DEEP::equals).count(), result.warnings().toString());
    }

    @Test
    void carriersOfParentChainsCountTowardsTheDepth() throws IOException {
        // five precompositions, each under a chain of 20 parents: 105 levels without the limit
        StringBuilder assets = new StringBuilder();
        for (int level = 1; level <= 5; level++) {
            assets.append(level == 1 ? "" : ",").append("{\"id\":\"c").append(level).append("\",\"layers\":[");
            assets.append(level < 5
                    ? "{\"ty\":0,\"ind\":1,\"parent\":2,\"ip\":0,\"op\":60,\"refId\":\"c" + (level + 1) + "\"}"
                    : "{\"ty\":4,\"ind\":1,\"parent\":2,\"ip\":0,\"op\":60," + BOX + "}");
            for (int ind = 2; ind <= 21; ind++) {
                assets.append(",{\"ty\":3,\"ind\":").append(ind).append(",\"ip\":0,\"op\":60")
                        .append(ind < 21 ? ",\"parent\":" + (ind + 1) : "").append("}");
            }
            assets.append("]}");
        }
        // and beside them a chain of 2000 parents
        StringBuilder layers = new StringBuilder("{\"ty\":0,\"ind\":5000,\"ip\":0,\"op\":60,\"refId\":\"c1\"}");
        for (int i = 1; i <= 2000; i++) {
            layers.append(",{\"ty\":3,\"ind\":").append(i).append(",\"ip\":0,\"op\":60")
                    .append(i < 2000 ? ",\"parent\":" + (i + 1) : "").append("}");
        }
        ImportResult result = lottie(lottieDocument(assets.toString(), layers.toString()));
        assertEquals(64, ImportChecks.measure(result.file().animation())[1]);
        assertWarns(result, TOO_DEEP);
        assertWarns(result, "Parent chains longer than 64 layers are not supported");
    }

    @Test
    void maskLayersCountTowardsTheLayerLimit() throws IOException {
        StringBuilder layers = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            layers.append(i == 0 ? "" : ",").append("{\"ty\":4,\"ip\":0,\"op\":60,").append(MASK).append(BOX).append("}");
        }
        String json = lottieDocument("", layers.toString());
        // also a guard on the time: checking 20,000 masks must not take a comparison per pair of layers
        ImportResult result = org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(3),
                () -> LottieImporter.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test"));
        ImportChecks.assertSavesAndRenders(result.file());
        int count = ImportChecks.measure(result.file().animation())[0];
        assertTrue(count > 15000 && count <= 20000, "layers: " + count);
        assertEquals(1, result.warnings().stream().filter(STOPPED::equals).count(), result.warnings().toString());
        // no layer is left without the mask it had
        for (Layer layer : result.file().animation().layers()) {
            assertTrue(layer.name().endsWith("(mask)") || layer.base().mask() != null, layer.name());
        }
    }

    @Test
    void aLottieBombWithMasksStopsAtTheLayerLimit() throws IOException {
        StringBuilder assets = new StringBuilder("{\"id\":\"c0\",\"layers\":[{\"ty\":4,\"ip\":0,\"op\":60," + MASK + BOX
                + "}]}");
        for (int level = 1; level <= 9; level++) {
            assets.append(",{\"id\":\"c").append(level).append("\",\"layers\":[");
            for (int i = 0; i < 10; i++) {
                assets.append(i == 0 ? "" : ",").append("{\"ty\":0,\"ip\":0,\"op\":60,").append(MASK)
                        .append("\"tt\":").append(i % 2).append(",\"refId\":\"c").append(level - 1).append("\"}");
            }
            assets.append("]}");
        }
        ImportResult result = lottie(lottieDocument(assets.toString(), "{\"ty\":0,\"ip\":0,\"op\":60,\"refId\":\"c9\"}"));
        int[] measured = ImportChecks.measure(result.file().animation());
        assertTrue(measured[0] > 1000, "layers: " + measured[0] + ", depth " + measured[1] + ", " + result.warnings());
        assertEquals(1, result.warnings().stream().filter(STOPPED::equals).count(), result.warnings().toString());
    }

    @Test
    void anSvgBombWithClipPathsStopsAtTheLayerLimit() throws IOException {
        StringBuilder svg = new StringBuilder(SVG).append("<defs><clipPath id='c'><rect width='1' height='1'/>"
                + "<circle r='1'/></clipPath><rect id='l0' width='1' height='1' clip-path='url(#c)'/>");
        for (int level = 1; level <= 9; level++) {
            svg.append("<g id='l").append(level).append("' clip-path='url(#c)'>");
            for (int i = 0; i < 10; i++) {
                svg.append("<use href='#l").append(level - 1).append("'/>");
            }
            svg.append("</g>");
        }
        svg.append("</defs><use href='#l9'/></svg>");
        ImportResult result = svg(svg.toString());
        assertTrue(ImportChecks.measure(result.file().animation())[0] > 1000);
        assertEquals(1, result.warnings().stream().filter(STOPPED::equals).count(), result.warnings().toString());
    }

    @Test
    void aDroppedLayerTakesItsMaskLayerAlong() throws IOException {
        // the text is dropped for its size; its mask shape must not stay behind as a visible layer
        ImportResult result = lottie(lottieDocument("", "{\"ty\":5,\"nm\":\"T\",\"ip\":0,\"op\":60," + MASK
                + "\"t\":{\"d\":{\"k\":[{\"t\":0,\"s\":{\"s\":1e300,\"f\":\"Arial\",\"t\":\"text\",\"fc\":[0,0,0]}}]}}},"
                + "{\"ty\":4,\"nm\":\"Other\",\"ip\":0,\"op\":60," + BOX + "}"));
        assertEquals(List.of("Other"), names(result.file().animation().layers()));
        assertWarns(result, "Layers with values that are not finite or too large were dropped (1 layer)");
    }

    @Test
    void aDroppedLayerTakesItsTrackMatteAlongUnlessAnotherLayerUsesIt() throws IOException {
        String matte = "{\"ty\":4,\"ind\":1,\"nm\":\"Matte\",\"td\":1,\"ip\":0,\"op\":60," + BOX + "},";
        String broken = "{\"ty\":1,\"ind\":2,\"nm\":\"Broken\",\"tt\":1,\"tp\":1,\"ip\":0,\"op\":60,\"sc\":\"#ff0000\","
                + "\"sw\":1e300,\"sh\":10}";
        ImportResult result = lottie(lottieDocument("", matte + broken));
        assertEquals(List.of(), result.file().animation().layers());
        assertWarns(result, "Layers with values that are not finite or too large were dropped (1 layer)");

        // a second user keeps the matte
        result = lottie(lottieDocument("", matte + broken + ",{\"ty\":4,\"ind\":3,\"nm\":\"User\",\"tt\":1,\"tp\":1,"
                + "\"ip\":0,\"op\":60," + BOX + "}"));
        Animation animation = result.file().animation();
        assertEquals(List.of("User", "Matte"), names(animation.layers()));
        assertEquals(new Mask(named(animation, "Matte").id(), Mask.Mode.ALPHA), named(animation, "User").base().mask());
    }

    // ------------------------------------------------------------------ sounds

    private static String sound(String asset, String layerMembers) {
        return lottieDocument(asset, "{\"ty\":6,\"nm\":\"Sound\",\"refId\":\"s\",\"ip\":0,\"op\":60" + layerMembers
                + "},{\"ty\":4,\"nm\":\"Other\",\"ip\":0,\"op\":60," + BOX + "}");
    }

    private static void assertSkipped(ImportResult result, String warning) {
        assertEquals(List.of("Other"), names(result.file().animation().layers()));
        assertEquals(List.of(), result.file().animation().assets());
        assertTrue(result.file().assets().isEmpty());
        assertEquals(List.of(warning), result.warnings());
    }

    @Test
    void aSoundThatCannotBeDecodedIsSkipped() throws IOException {
        String undecodable = "Embedded sounds that cannot be decoded or are empty were skipped (1 layer)";
        assertSkipped(lottie(sound("{\"id\":\"s\",\"e\":1,\"p\":\"data:audio/wav;base64,!!not base64!!\"}", "")),
                undecodable);
        assertSkipped(lottie(sound("{\"id\":\"s\",\"e\":1,\"p\":\"data:audio/wav;base64,AAECA\"}", "")), undecodable);
        assertSkipped(lottie(sound("{\"id\":\"s\",\"e\":1,\"p\":\"data:audio/wav;base64,\"}", "")), undecodable);
        assertSkipped(lottie(sound("{\"id\":\"s\",\"e\":1,\"p\":\"data:audio/wav,plain\"}", "")), undecodable);
    }

    @Test
    void aSoundOfMoreThan50MegabytesIsSkipped() throws IOException {
        String payload = "AAAA".repeat(50 * 1024 * 1024 / 3 + 1);
        ImportResult result = lottie(sound("{\"id\":\"s\",\"e\":1,\"p\":\"data:audio/wav;base64," + payload + "\"}",
                "").replace("{\"ty\":6,", "{\"ty\":6,\"ind\":7,\"refId\":\"s\"},{\"ty\":6,"));
        assertSkipped(result, "Embedded sounds of more than 50 MB were skipped (2 layers)");
    }

    @Test
    void anAudioLayerWhoseAssetIsNoSoundIsSkipped() throws IOException {
        String missing = "Audio layers whose sound is missing from the file were skipped (1 layer)";
        String noSound = "Audio layers whose asset is not a sound were skipped (1 layer)";
        // a tiny PNG header as an image asset, a precomposition, nothing at all, no reference
        assertSkipped(lottie(sound("{\"id\":\"s\",\"e\":1,\"p\":\"data:image/png;base64,iVBORw0KGgo=\"}", "")), noSound);
        assertSkipped(lottie(sound("{\"id\":\"s\",\"layers\":[]}", "")), noSound);
        assertSkipped(lottie(sound("{\"id\":\"s\"}", "")), missing);
        assertSkipped(lottie(sound("", "")), missing);
        assertSkipped(lottie(sound("{\"id\":\"s\",\"p\":17,\"u\":{}}", "")), missing);
    }

    @Test
    void soundValuesThatAreAbsurdStayWithinTheRangesOfTheFormat() throws IOException {
        String asset = "{\"id\":\"s\",\"e\":1,\"p\":\"data:audio/wav;base64,AAECAw==\"}";
        ImportResult result = lottie(sound(asset, ",\"st\":-1e300,\"au\":{\"lv\":{\"a\":1,\"k\":["
                + "{\"t\":0,\"s\":[1e300,-1e300]},{\"t\":30,\"s\":[-1e300]},{\"t\":60,\"s\":[0,0]}]}}"));
        Animation animation = result.file().animation();
        assertEquals(List.of("Other", "Sound"), names(animation.layers()));
        com.dlsc.animatorfx.model.AudioLayer layer = (com.dlsc.animatorfx.model.AudioLayer) animation.layers().get(1);
        assertEquals(0, layer.clipOffset());
        for (double time : new double[]{0, 0.5, 1, 1.5, 2}) {
            double volume = layer.volume().at(time);
            assertTrue(volume >= 0 && volume <= 1, "volume at " + time + ": " + volume);
        }

        // in and out points far outside the document
        result = lottie(sound(asset, ",\"st\":1e12").replace("\"ip\":0,\"op\":60,\"st\"", "\"ip\":-1e12,\"op\":1e12,\"st\""));
        layer = (com.dlsc.animatorfx.model.AudioLayer) result.file().animation().layers().get(1);
        assertEquals(0, layer.base().inPoint());
        assertEquals(2, layer.base().outPoint());
        assertEquals(0, layer.clipOffset());
    }
}
