import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws the application icon: an easing curve between two keyframe diamonds on a dark rounded
 * square. Run by make-icons.sh as a single-file program: {@code java IconGenerator.java out.png}.
 */
public final class IconGenerator {

    private static final int SIZE = 1024;

    public static void main(String[] args) throws IOException {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // the macOS icon grid: an 824 px rounded square with a soft shadow below it
        RoundRectangle2D tile = new RoundRectangle2D.Double(100, 100, 824, 824, 370, 370);
        for (int i = 24; i > 0; i--) {
            g.setColor(new Color(0, 0, 0, 2));
            g.fill(new RoundRectangle2D.Double(100 - i * 0.4, 106 + i * 0.6, 824 + i * 0.8, 824 + i * 0.4, 370 + i, 370 + i));
        }
        g.setPaint(new GradientPaint(0, 100, new Color(0x27, 0x30, 0x4A), 0, 924, new Color(0x0D, 0x12, 0x20)));
        g.fill(tile);

        g.setClip(tile);
        // a faint timeline grid
        g.setColor(new Color(255, 255, 255, 14));
        g.setStroke(new BasicStroke(3));
        for (int i = 1; i < 6; i++) {
            double p = 100 + 824 * i / 6.0;
            g.draw(new Rectangle2D.Double(p, 100, 0.01, 824));
            g.draw(new Rectangle2D.Double(100, p, 824, 0.01));
        }
        // a soft highlight along the top edge
        g.setPaint(new GradientPaint(0, 100, new Color(255, 255, 255, 46), 0, 420, new Color(255, 255, 255, 0)));
        g.fill(new Rectangle2D.Double(100, 100, 824, 320));
        g.setClip(null);
        g.setPaint(new GradientPaint(0, 100, new Color(255, 255, 255, 70), 0, 924, new Color(255, 255, 255, 12)));
        g.setStroke(new BasicStroke(4));
        g.draw(new RoundRectangle2D.Double(102, 102, 820, 820, 366, 366));

        // the easing curve from the lower left keyframe to the upper right one
        double x1 = 290;
        double y1 = 712;
        double x2 = 734;
        double y2 = 312;
        CubicCurve2D curve = new CubicCurve2D.Double(x1, y1, x1 + 300, y1, x2 - 300, y2, x2, y2);
        g.setStroke(new BasicStroke(112, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x38, 0xBD, 0xF8, 34));
        g.draw(curve);
        g.setStroke(new BasicStroke(58, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setPaint(new LinearGradientPaint((float) x1, (float) y1, (float) x2, (float) y2, new float[]{0, 1},
                new Color[]{new Color(0x4F, 0x6B, 0xF5), new Color(0x22, 0xD3, 0xEE)}));
        g.draw(curve);

        diamond(g, x1, y1, 86);
        diamond(g, x2, y2, 86);
        g.dispose();

        File out = new File(args.length > 0 ? args[0] : "AnimatorFX.png");
        ImageIO.write(image, "png", out);
        System.out.println("Wrote " + out);
    }

    /** An amber keyframe diamond with a dark rim, so that it stands clear of the curve. */
    private static void diamond(Graphics2D g, double x, double y, double radius) {
        Path2D shape = new Path2D.Double();
        shape.moveTo(0, -radius);
        shape.lineTo(radius, 0);
        shape.lineTo(0, radius);
        shape.lineTo(-radius, 0);
        shape.closePath();
        AffineTransform saved = g.getTransform();
        g.translate(x, y);
        g.setStroke(new BasicStroke(44, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x14, 0x1A, 0x2C));
        g.draw(shape);
        g.fill(shape);
        g.setStroke(new BasicStroke(18, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setPaint(new GradientPaint(0, (float) -radius, new Color(0xFC, 0xD3, 0x4D), 0, (float) radius,
                new Color(0xF5, 0x9E, 0x0B)));
        g.draw(shape);
        g.fill(shape);
        g.setTransform(saved);
    }
}
