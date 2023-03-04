package nu.marginalia.memex.dithering;

import nu.marginalia.memex.util.dithering.FloydSteinbergDither;
import nu.marginalia.memex.util.dithering.Palettes;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

class FloydSteinbergDitherTest {

    @Test
    public void test() throws IOException {
//        convert("/home/vlofgren/Work/dither/volvo.jpg", "/home/vlofgren/Work/dither/volvo-raster.png");
//        convert("/home/vlofgren/Work/dither/dog.jpg", "/home/vlofgren/Work/dither/dog-raster.png");
//        convert("/home/vlofgren/Work/dither/robocop.jpg", "/home/vlofgren/Work/dither/robocop-raster.png");
//        convert("/home/vlofgren/Work/dither/socrates.jpeg", "/home/vlofgren/Work/dither/socrates-raster.png");


//        convert("C:\\Users\\vlofg\\Documents\\volvo.jpg",
//                "C:\\Users\\vlofg\\Documents\\volvo-raster.png");
//        convert("C:\\Users\\vlofg\\Documents\\socrates.jpg",
//                "C:\\Users\\vlofg\\Documents\\socrates-raster.png");
//        convert("C:\\Users\\vlofg\\Documents\\goya_nude_maja.jpg",
//                "C:\\Users\\vlofg\\Documents\\goya_nude_maja-raster.png");
    }

    void convert(String in, String out) throws IOException {
        var result = new FloydSteinbergDither(Palettes.MARGINALIA_PALETTE, 640, 480).convert(ImageIO.read(new File(in)));

        ImageIO.write(result, "png", new File(out));
    }
}