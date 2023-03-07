package nu.marginalia.array.trace;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;

public class ArrayTraceViz {


    private static final int BLOCK_SIZE_WORDS = 512;

    public static void main(String[] args) throws IOException {
        Path inputFile = Path.of("/home/vlofgren/array-trace.log");
        Map<Integer, Integer> sizes = new HashMap<>();
        Map<Integer, Set<Integer>> rows = new HashMap<>();

        try (var lines = Files.lines(inputFile)) {
            lines.map(line -> line.split("\\s")).forEach(parts -> {
                int block = Integer.parseInt(parts[1]);
                int start = Integer.parseInt(parts[2]);
                int end = Integer.parseInt(parts[3]);

                sizes.merge(block, end, Integer::max);

                var rowSet = rows.computeIfAbsent(block, b -> new HashSet<>());
                for (int b = start; b < end; b += BLOCK_SIZE_WORDS) {
                    rowSet.add(b/BLOCK_SIZE_WORDS);
                }
            });
        }

        Map<Integer, Map<Integer, Integer>> rowToY = new HashMap<>();

        rows.forEach((row, vals) -> {
            var map = new HashMap<Integer, Integer>(vals.size());
            rowToY.put(row, map);
            var list = new ArrayList<>(vals);

            list.stream().sorted().forEach(val -> map.put(val, map.size()));
        });

        Map<Integer, Integer> cols = new HashMap<>();
        sizes.keySet().forEach(key -> cols.put(key, cols.size()));

        int width = cols.size() * (BLOCK_SIZE_WORDS+4);
        int height = 640;

        var bi = new BufferedImage(width, height, TYPE_INT_RGB);

        AtomicInteger iv = new AtomicInteger();

        try (var lines = Files.lines(inputFile)) {
            lines.forEach(line -> {
                String[] parts = line.split("\\s");

                long time = Long.parseLong(parts[0]);
                int block = Integer.parseInt(parts[1]);
                int start = Integer.parseInt(parts[2]);
                int end = Integer.parseInt(parts[3]);

                for (int p = start; p < end; p++) {
                    int x0 = (4+BLOCK_SIZE_WORDS) * cols.get(block);
                    int x = x0 + (p%BLOCK_SIZE_WORDS);
                    int y = rowToY.get(block).get(p/BLOCK_SIZE_WORDS);

                    if (y >= 640) {
                        continue;
                    }

                    if (0 == bi.getRGB(x, y)) {
                        for (int x2 = 0; x2 < BLOCK_SIZE_WORDS; x2++) {
                            if (0 == bi.getRGB(x0 + x2, y)) {
                                bi.setRGB(x0 + x2, y, 0xC0C0C0);
                            }
                        }
                    }

                    System.out.println(x + "," + y);
                    bi.setRGB(x, y, (int) (0xFFFFFFL));
                }

                try {
                    if ((iv.incrementAndGet() % 4) == 0) {
                        ImageIO.write(bi, "png", new File("/tmp/test" + (time * Long.signum(time)) + " .png"));
                        for (int x = 0; x < width; x++) {
                            for (int y = 0; y < height; y++) {
                                int val = bi.getRGB(x, y);
                                int nval = (val&0xFF) - 1;
                                if (nval > 64) {
                                    bi.setRGB(x, y, nval | (nval<<8) | (nval << 16));
                                }
                                else if ((val&0xFFFFFF) != 0) {
                                    bi.setRGB(x, y, 64);
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }


    }

    record ArrayPage(int id, int size) {}
}
