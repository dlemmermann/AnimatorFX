package com.dlsc.animatorfx.importer;

import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.GroupLayer;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerBase;
import com.dlsc.animatorfx.model.io.AnimationFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts an SVG document to static layers.
 */
public final class SvgImporter {

    static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";
    private static final String XLINK_NAMESPACE = "http://www.w3.org/1999/xlink";

    /** Seconds; an SVG has no duration of its own. */
    static final double DURATION = 3;

    private static final double DEFAULT_WIDTH = 300;
    private static final double DEFAULT_HEIGHT = 150;

    private SvgImporter() {
    }

    /**
     * Reads an SVG document. The result is 3 seconds long at 60 frames per second with a
     * transparent background. Features that cannot be converted are skipped and named in the
     * warnings.
     *
     * @param name the name of the document
     * @throws IOException if the input cannot be read or is not an SVG document
     */
    public static ImportResult read(InputStream in, String name) throws IOException {
        Element root = parse(in).getDocumentElement();
        if (root == null || !"svg".equals(root.getLocalName()) || !isSvg(root)) {
            throw new IOException("Not an SVG document: the root element is <"
                    + (root == null ? "" : root.getNodeName()) + ">");
        }
        Warnings warnings = new Warnings();
        Map<String, Element> ids = scan(root, warnings);

        double[] viewBox = viewBox(root);
        double width = size(root.getAttribute("width"), viewBox == null ? DEFAULT_WIDTH : viewBox[2], DEFAULT_WIDTH,
                warnings);
        double height = size(root.getAttribute("height"), viewBox == null ? DEFAULT_HEIGHT : viewBox[3],
                DEFAULT_HEIGHT, warnings);
        double[] rootMatrix = viewBox == null ? Matrix.identity()
                : fit(viewBox, width, height, root.getAttribute("preserveAspectRatio"), warnings);

        SvgLayers layers = new SvgLayers(ids, warnings, viewBox == null ? width : viewBox[2],
                viewBox == null ? height : viewBox[3]);
        SvgStyle style = SvgStyle.of(root, null);
        List<Layer> content = layers.children(root, style);
        double opacity = SvgLayers.opacity(style, warnings);
        if (!Matrix.isIdentity(rootMatrix) || opacity != 1) {
            String id = root.getAttribute("id");
            LayerBase base = LayerBase.create(id.isEmpty() ? "svg" : id, DURATION)
                    .withTransform(Matrix.decompose(rootMatrix)).withOpacity(Animatable.of(opacity));
            content = List.of(new GroupLayer(base, content));
        }
        Animation animation = DocumentCheck.repair(Animation.create(name, width, height, DURATION)
                .withAssets(layers.assets()).withLayers(content), warnings);
        return new ImportResult(new AnimationFile(animation, layers.assetBytes()), warnings.lines());
    }

    /**
     * Parses without ever fetching anything: external DTDs, external entities and XInclude are
     * switched off. A document type declaration itself is allowed, because exported SVG files
     * commonly carry one and declare internal entities.
     */
    private static Document parse(InputStream in) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                }

                @Override
                public void error(SAXParseException exception) {
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    throw exception;
                }
            });
            return builder.parse(in);
        } catch (SAXException e) {
            throw new IOException("Not an SVG document: " + e.getMessage(), e);
        } catch (ParserConfigurationException e) {
            throw new IOException("The XML parser cannot be configured safely", e);
        }
    }

    static boolean isSvg(Element element) {
        String namespace = element.getNamespaceURI();
        return namespace == null || namespace.equals(SVG_NAMESPACE);
    }

    /** The {@code href} of an element, also in its older {@code xlink:href} form. */
    static String href(Element element) {
        String href = element.getAttribute("href");
        return (href.isEmpty() ? element.getAttributeNS(XLINK_NAMESPACE, "href") : href).trim();
    }

    /** Indexes the elements by id and reports the unsupported ones wherever they are. */
    private static Map<String, Element> scan(Element root, Warnings warnings) {
        Map<String, Element> ids = new HashMap<>();
        NodeList all = root.getElementsByTagNameNS("*", "*");
        for (int i = -1; i < all.getLength(); i++) {
            Element element = i < 0 ? root : (Element) all.item(i);
            if (!isSvg(element)) {
                continue;
            }
            String id = element.getAttribute("id");
            if (!id.isEmpty()) {
                ids.putIfAbsent(id, element);
            }
            switch (element.getLocalName()) {
                case "style" -> warnings.add("CSS stylesheets are not supported", "<style> element ignored",
                        "<style> elements ignored");
                case "filter" -> ignored(warnings, "Filters are not supported");
                case "pattern" -> ignored(warnings, "Patterns are not supported");
                case "mask" -> ignored(warnings, "Masks are not supported");
                case "marker" -> ignored(warnings, "Markers are not supported");
                case "animate", "animateTransform", "animateMotion", "animateColor", "set" ->
                        ignored(warnings, "SMIL animation is not supported");
                case "foreignObject" -> ignored(warnings, "Foreign objects are not supported");
                case "textPath" -> ignored(warnings, "Text on a path is not supported");
                default -> {
                }
            }
        }
        return ids;
    }

    static void ignored(Warnings warnings, String message) {
        warnings.add(message, "element ignored", "elements ignored");
    }

    private static double[] viewBox(Element root) {
        double[] numbers = SvgTransforms.numbers(root.getAttribute("viewBox"));
        return numbers != null && numbers.length == 4 && Limits.usable(numbers) && numbers[2] > 0 && numbers[3] > 0
                ? numbers : null;
    }

    private static double size(String text, double fromViewBox, double fallback, Warnings warnings) {
        double size = SvgLength.isPercentage(text) ? fromViewBox : SvgLength.parse(text, 0, fromViewBox, warnings);
        return size > 0 ? size : fallback;
    }

    /** Maps the view box into the document. */
    private static double[] fit(double[] viewBox, double width, double height, String preserveAspectRatio,
                                Warnings warnings) {
        double scaleX = width / viewBox[2];
        double scaleY = height / viewBox[3];
        String mode = preserveAspectRatio.trim().replaceAll("\\s+", " ");
        if (mode.equals("none")) {
            return Matrix.multiply(Matrix.scale(scaleX, scaleY), Matrix.translate(-viewBox[0], -viewBox[1]));
        }
        if (!(mode.isEmpty() || mode.equals("xMidYMid") || mode.equals("xMidYMid meet"))) {
            warnings.add("preserveAspectRatio values other than xMidYMid meet and none are not supported;"
                    + " the default is used");
        }
        double scale = Math.min(scaleX, scaleY);
        double[] centred = Matrix.translate((width - viewBox[2] * scale) / 2, (height - viewBox[3] * scale) / 2);
        return Matrix.multiply(centred,
                Matrix.multiply(Matrix.scale(scale, scale), Matrix.translate(-viewBox[0], -viewBox[1])));
    }
}
