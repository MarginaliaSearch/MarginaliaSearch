package bs_vs_ls;

import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.util.multimap.MultimapSearcher;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.LongStream;

public class BinSearchVsLinSearch2 {
    static long[] data = LongStream.generate(() -> (long) (Long.MAX_VALUE * Math.random())).limit(512).sorted().toArray();

    @State(Scope.Benchmark)
    public static class Target {
        Path tf;
        MultimapFileLong file;
        MultimapSearcher searcher;
        final long[] data = new long[512];

        {
            try {
                tf = Files.createTempFile("tmpFileIOTest", "dat");
                file = MultimapFileLong.forOutput(tf, 1024);
                searcher = file.createSearcher();
                for (int i = 0; i < 65535; i++) {
                    file.put(i, i);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Measurement(iterations = 1)
    @Warmup(iterations = 1)
    @Benchmark
    public long testLs(Target t) {
        int target = (int)(4096 + 512 * Math.random());
        for (int i = 4096; i < (4096+512); i++) {
            long val = t.file.get(i);
            if (val > target)
                break;
            if (val == target)
                return val;
        }
        return -1;
    }

    @Measurement(iterations = 1)
    @Warmup(iterations = 1)
    @Benchmark
    public long testLs2(Target t) {
        int target = (int)(4096 + 512 * Math.random());

        t.file.read(t.data, 4096);
        for (int i = 0; i < (512); i++) {
            long val = t.file.get(i);
            if (val > target)
                break;
            if (val == target)
                return val;
        }
        return -1;
    }

}
