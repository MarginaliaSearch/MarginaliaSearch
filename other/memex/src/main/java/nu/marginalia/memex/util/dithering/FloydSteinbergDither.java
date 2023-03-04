package nu.marginalia.memex.util.dithering;

import lombok.AllArgsConstructor;
import net.sf.image4j.util.ConvertUtil;
import org.imgscalr.Scalr;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.util.Arrays;
import java.util.Comparator;

public class FloydSteinbergDither {
    private final Color[] palette;

    private final int maxWidth;
    private final int maxHeight;

    public FloydSteinbergDither(int[] colors, int maxWidth, int maxHeight) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;

        palette = Arrays.stream(colors)
                    .mapToObj(Color::new)
                    .toArray(Color[]::new);
    }

    public BufferedImage convert(BufferedImage src) {
        BufferedImage out = dither(resize(src));

        if (palette.length <= 16) {
            int[] cmap = new int[palette.length];
            for (int i = 0; i < palette.length; i++) {
                cmap[i] = palette[i].toInt();
            }
            return ConvertUtil.convert4(out, cmap);
        }
        return out;
    }

    private BufferedImage dither(BufferedImage in) {

        Errors errors = new Errors(in.getWidth(), in.getHeight());

        final BufferedImage out = createOutBuffer(in);

        for (int y = 0; y < in.getHeight(); y++) {
            for (int x = 0; x < in.getWidth(); x++) {
                setOutPixel(errors, out, in, x, y, 1);
            }
            if (++y >= in.getHeight()) {
                break;
            }
            for (int x = in.getWidth()-1; x >= 0; x--) {
                setOutPixel(errors, out, in, x, y, -1);
            }
        }
        return out;
    }

    private void setOutPixel(Errors errors, BufferedImage out, BufferedImage in, int x, int y, int dx) {
        final Color color = new Color(in.getRGB(x, y));
        final Color adjustedColor = errors.adjust(color, x, y);

        final int newColor = getNearestColorAndDiffuseError(errors,
                                x, dx, y,
                                adjustedColor, color);

        out.setRGB(x, y, newColor);
    }

    private BufferedImage createOutBuffer(BufferedImage in) {

        var indexModel = createIndexColorModel();

        return new BufferedImage(indexModel,
                indexModel.createCompatibleWritableRaster(in.getWidth(), in.getHeight()),
                false, null);

    }

    private BufferedImage resize(BufferedImage src) {
        if (maxWidth < 0 || maxHeight < 0) {
            return src;
        }
        final int width = src.getWidth();
        final int height = src.getHeight();

        double scaleF = Math.min(scaleFactor(width, maxWidth),
                                 scaleFactor(height, maxHeight));

        if (scaleF < 1.0) {
            int newWidth = (int)Math.min(maxWidth, scaleF * width);
            int newHeight = (int)Math.min(maxHeight, scaleF * height);

            return Scalr.resize(src,
                    Scalr.Method.QUALITY,
                    Scalr.Mode.AUTOMATIC,
                    newWidth, newHeight);
        }

        return src;
    }

    private double scaleFactor(int actualValue, int desiredValue) {
        if (actualValue <= desiredValue) {
            return 1.;
        }
        return desiredValue / (double) actualValue;
    }

    private IndexColorModel createIndexColorModel() {
        byte[] reds = new byte[palette.length];
        byte[] greens = new byte[palette.length];
        byte[] blues = new byte[palette.length];

        for (int i = 0; i < palette.length; i++) {
            int colorInt = palette[i].toInt();

            reds[i] = (byte) ((colorInt >>> 16) & 0xFF);
            greens[i] = (byte) ((colorInt >>> 8) & 0xFF);
            blues[i] = (byte) ((colorInt) & 0xFF);
        }

        return new IndexColorModel(getPaletteBits(palette), palette.length, reds, greens, blues);

    }

    private int getPaletteBits(Color[] palette) {
        if (palette.length <= 16) {
            return 4;
        }
        else {
            return 8;
        }
    }

    private int getNearestColorAndDiffuseError(Errors errors, int x, int dx, int y, Color color, Color colorOrig) {

        var match = Arrays.stream(palette).min(Comparator.comparing(c -> c.delta(color)));
        assert match.isPresent();

        var retC = match.get();
        var error = colorOrig.minus(retC);

        errors.add(x+dx, y, error.scale(7/16.));
        errors.add(x+dx, y+1, error.scale(1/16.));
        errors.add(x, y+1, error.scale(5/16.));
        errors.add(x-dx, y+1, error.scale(3/16.));

        return retC.toInt();
    }
}

class Errors {
    private final int width;
    private final int height;
    private final Color[] errors;

    Errors(int width, int height) {
        this.width = width;
        this.height = height;

        errors = new Color[width * height];
    }

    public void add(int x, int y, Color color) {
        if (x > 0 && y > 0 && x + 1 < width && y + 1 < height) {
            int index = getIndex(x, y);
            if (errors[index] == null) {
                errors[index] = color;
            }
            else {
                errors[index] = errors[index].plus(color);
            }
        }
    }

    public Color adjust(Color in, int x, int y) {
        int idx = getIndex(x, y);

        if (errors[idx] != null) {
            return in.plus(errors[idx]);
        }
        return in;
    }

    private int getIndex(int x, int y) {
        return x * height + y;
    }
}

@AllArgsConstructor
class Color {
    private final double r;
    private final double g;
    private final double b;

    Color(int hex) {
        this.b = ((hex) & 0xFF);
        this.g = ((hex >>> 8) & 0xFF);
        this.r = ((hex >>> 16) & 0xFF);
    }

    int toInt() {
        double bv = clampByteRange(b);
        double gv = clampByteRange(g);
        double rv = clampByteRange(r);

        return (((int)bv&0xFF) | (((int)gv & 0xFF) << 8) | (((int)rv & 0xFF) << 16));
    }

    double clampByteRange(double v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    public Color scale(double factor) {
        return new Color(r*factor, g*factor, b*factor);
    }

    public Color plus(Color other) {
        return new Color(r+other.r, g+other.g, b+other.b);
    }

    public Color minus(Color other) {
        return new Color(r-other.r, g-other.g, b-other.b);
    }

    public double delta(Color other) {
        double avgr = (r + other.r)/2;
        double dr = r - other.r;
        double dg = g - other.g;
        double db = b - other.b;

        if (avgr > 128) {
            return Math.sqrt(2 * dr * dr + 4 * dg * dg + 3 * db * db);
        }
        else {
            return Math.sqrt(3 * dr * dr + 4 * dg * dg + 2 * db * db);
        }
    }
}
