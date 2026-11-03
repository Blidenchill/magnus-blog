package com.hubc.magnusblog.media;

import com.hubc.magnusblog.common.InvalidInputException;
import com.hubc.magnusblog.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessingServiceTest {

    private final ImageProcessingService service = new ImageProcessingService(new AppProperties(
            new AppProperties.Storage(Path.of("target/it-storage"), 0),
            new AppProperties.Admin("admin"),
            new AppProperties.Upload(10_000_000, 12_000, 12_000, 2),
            new AppProperties.Site("http://localhost:8080")));

    @TempDir
    Path tempDir;

    private Path image(String name, int w, int h) throws Exception {
        return image(name, w, h, name.endsWith(".png") ? "png" : "jpg");
    }

    private Path image(String name, int w, int h, String format) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFF0000);
        Path file = tempDir.resolve(name);
        ImageIO.write(img, format, Files.newOutputStream(file));
        return file;
    }

    @Test
    void decodesPngAndJpeg() throws Exception {
        assertThat(service.decode(image("a.png", 100, 80)).format())
                .isEqualTo(ImageProcessingService.Format.PNG);
        assertThat(service.decode(image("b.jpg", 200, 100)).format())
                .isEqualTo(ImageProcessingService.Format.JPEG);
    }

    @Test
    void rejectsGarbageBytes() throws Exception {
        Path junk = tempDir.resolve("junk.png");
        Files.write(junk, "not an image".getBytes());
        assertThatThrownBy(() -> service.decode(junk)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void rejectsOversizeDimensions() throws Exception {
        // width limit is 12000; a 12001-wide image is rejected before full decode
        Path big = image("wide.jpg", 12_001, 10, "jpg");
        assertThatThrownBy(() -> service.decode(big)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void displayVariantScalesLongEdge() throws Exception {
        BufferedImage src = new BufferedImage(4000, 1000, BufferedImage.TYPE_INT_RGB);
        Path out = tempDir.resolve("display.jpg");
        int[] size = service.writeDisplay(src, out);
        assertThat(size[0]).isEqualTo(ImageProcessingService.DISPLAY_LONG_EDGE);
        assertThat(size[1]).isEqualTo(480);
        assertThat(Files.size(out)).isGreaterThan(0);
        // re-decodable and stripped of metadata
        BufferedImage back = ImageIO.read(Files.newInputStream(out));
        assertThat(back.getWidth()).isEqualTo(ImageProcessingService.DISPLAY_LONG_EDGE);
    }

    @Test
    void thumbFitsWithinBoxKeepingAspect() throws Exception {
        BufferedImage src = new BufferedImage(1600, 900, BufferedImage.TYPE_INT_RGB);
        Path out = tempDir.resolve("thumb.jpg");
        int[] size = service.writeThumb(src, out);
        assertThat(size[0]).isLessThanOrEqualTo(ImageProcessingService.THUMB_MAX_W);
        assertThat(size[1]).isLessThanOrEqualTo(ImageProcessingService.THUMB_MAX_H);
    }

    @Test
    void smallImageIsNotUpscaled() throws Exception {
        BufferedImage src = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        Path out = tempDir.resolve("thumb2.jpg");
        int[] size = service.writeThumb(src, out);
        assertThat(size).containsExactly(320, 200);
    }
}
