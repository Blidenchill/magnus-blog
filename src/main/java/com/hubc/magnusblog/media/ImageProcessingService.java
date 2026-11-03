package com.hubc.magnusblog.media;

import com.hubc.magnusblog.common.InvalidInputException;
import com.hubc.magnusblog.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;

/**
 * Image validation, dimension checks and re-encoding with ImageIO.
 * Re-encoding strips EXIF and any embedded metadata from every stored variant,
 * including the "original" (we keep original resolution, not original bytes).
 */
@Service
public class ImageProcessingService {

    public static final int DISPLAY_LONG_EDGE = 1920;
    public static final int THUMB_MAX_W = 800;
    public static final int THUMB_MAX_H = 600;

    private final long maxPixels;
    private final int maxWidth;
    private final int maxHeight;

    public ImageProcessingService(AppProperties properties) {
        this.maxPixels = properties.upload().maxPixels();
        this.maxWidth = properties.upload().maxWidth();
        this.maxHeight = properties.upload().maxHeight();
    }

    public enum Format {
        JPEG("jpeg", "jpg", "image/jpeg"),
        PNG("png", "png", "image/png");

        final String imageIoName;
        final String extension;
        final String mime;

        Format(String imageIoName, String extension, String mime) {
            this.imageIoName = imageIoName;
            this.extension = extension;
            this.mime = mime;
        }
    }

    public record Decoded(BufferedImage image, Format format) {
    }

    /**
     * Decode an uploaded image with decompression-bomb protection: header
     * dimensions are validated before the full pixel read.
     */
    public Decoded decode(Path file) {
        try (InputStream in = Files.newInputStream(file);
             ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis == null) {
                throw new InvalidInputException("file", "无法识别的图片数据");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new InvalidInputException("file", "无法识别的图片数据");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                String formatName = reader.getFormatName().toLowerCase(Locale.ROOT);
                Format format = switch (formatName) {
                    case "jpeg", "jpg" -> Format.JPEG;
                    case "png" -> Format.PNG;
                    default -> throw new InvalidInputException("file",
                            "仅支持 JPEG 和 PNG 图片（实际格式为 " + formatName + "）");
                };
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w <= 0 || h <= 0) {
                    throw new InvalidInputException("file", "图片尺寸无效");
                }
                if (w > maxWidth || h > maxHeight || (long) w * h > maxPixels) {
                    throw new InvalidInputException("file",
                            "图片像素超出限制（最大 " + maxPixels + " 像素）");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new InvalidInputException("file", "图片内容为空");
                }
                return new Decoded(image, format);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new InvalidInputException("file", "图片读取失败");
        }
    }

    /** Re-encode at original resolution (drops all metadata/EXIF). */
    public void writeOriginal(BufferedImage image, Format format, Path target) throws IOException {
        if (format == Format.PNG) {
            writePng(image, target);
        } else {
            writeJpeg(toRgb(image), target, 0.92f);
        }
    }

    /** Long edge limited to {@link #DISPLAY_LONG_EDGE}; always JPEG. */
    public int[] writeDisplay(BufferedImage image, Path target) throws IOException {
        BufferedImage scaled = scaleToLongEdge(image, DISPLAY_LONG_EDGE);
        writeJpeg(toRgb(scaled), target, 0.85f);
        return new int[]{scaled.getWidth(), scaled.getHeight()};
    }

    /** Fit inside {@link #THUMB_MAX_W}x{@link #THUMB_MAX_H}, aspect preserved; always JPEG. */
    public int[] writeThumb(BufferedImage image, Path target) throws IOException {
        int w = image.getWidth();
        int h = image.getHeight();
        double ratio = Math.min((double) THUMB_MAX_W / w, (double) THUMB_MAX_H / h);
        if (ratio >= 1.0) {
            writeJpeg(toRgb(image), target, 0.8f);
            return new int[]{w, h};
        }
        BufferedImage scaled = scale(image, (int) Math.round(w * ratio), (int) Math.round(h * ratio));
        writeJpeg(toRgb(scaled), target, 0.8f);
        return new int[]{scaled.getWidth(), scaled.getHeight()};
    }

    private static BufferedImage scaleToLongEdge(BufferedImage image, int longEdge) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (Math.max(w, h) <= longEdge) {
            return image;
        }
        double ratio = (double) longEdge / Math.max(w, h);
        return scale(image, (int) Math.round(w * ratio), (int) Math.round(h * ratio));
    }

    private static BufferedImage scale(BufferedImage image, int w, int h) {
        BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, out.getWidth(), out.getHeight(), null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static void writePng(BufferedImage image, Path target) throws IOException {
        try (OutputStream out = Files.newOutputStream(target)) {
            ImageIO.write(image, "png", out);
        }
    }

    private static void writeJpeg(BufferedImage image, Path target, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("no jpeg writer");
        }
        ImageWriter writer = writers.next();
        try (OutputStream out = Files.newOutputStream(target);
             ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
