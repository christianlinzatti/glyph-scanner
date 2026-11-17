import io.github.christianlinzatti.glyphscanner.core.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class ScreenshotTest {
    static Frame frame(BufferedImage img) {
        return new Frame() {
            public int width() { return img.getWidth(); }
            public int height() { return img.getHeight(); }
            public int rgb(int x, int y) { return img.getRGB(x, y) & 0xffffff; }
        };
    }

    static BufferedImage scale(BufferedImage src, double s) {
        int w = (int) (src.getWidth() * s), h = (int) (src.getHeight() * s);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("base edges: " + GlyphGeometry.EDGE_COUNT + ", dictionary: " + GlyphDictionary.size());
        double[] scales = {1.0, 0.5, 1080.0 / 1008.0, 0.4};
        for (double s : scales) {
            System.out.println("=== scale " + s);
            for (String p : args) {
                ScreenAnalyzer an = new ScreenAnalyzer(); // fresh: tests calibration per image
                BufferedImage img = scale(ImageIO.read(new File(p)), s);
                Analysis a = an.analyze(frame(img));
                String code = GlyphGeometry.toCode(a.edges);
                System.out.printf("%-22s screen=%b input=%b n=%d cur=%d code=%s name=%s | %s%n",
                        new File(p).getName(), a.glyphScreen, a.inputPhase, a.count, a.current, code,
                        GlyphDictionary.names(a.edges), a.debug);
                if (s == 1.0 && an.isCalibrated())
                    System.out.printf("    calib cy=%.1f r=%.1f%n", an.centerY(), an.radius());
            }
        }
    }
}
