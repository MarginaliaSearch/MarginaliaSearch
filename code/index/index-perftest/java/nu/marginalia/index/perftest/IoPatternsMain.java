package nu.marginalia.index.perftest;

import nu.marginalia.ffi.LinuxSystemCalls;
import nu.marginalia.uring.UringFileReader;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.LongStream;

public class IoPatternsMain {

    static void testBuffered(int sz, int small, int large, int iters) {
        try {
            Path largeFile = Path.of("/home/vlofgren/largefile.dat");
            long fileSize = Files.size(largeFile);

            Random r = new Random();
            List<MemorySegment> segments = new ArrayList<>();
            for (int i = 0; i < sz; i++) {
                if (small == large) {
                    segments.add(Arena.ofAuto().allocate(small));
                }
                else {
                    segments.add(Arena.ofAuto().allocate(r.nextInt(small, large)));
                }
            }
            List<Long> offsets = new ArrayList<>();

            long[] samples = new long[1000];
            int si = 0;

            try (UringFileReader reader = new UringFileReader(largeFile, false)) {
                for (int iter = 0; iter < iters; ) {
                    if (si == samples.length) {
                        Arrays.sort(samples);
                        double p1 = samples[10] / 1_000.;
                        double p10 = samples[100] / 1_000.;
                        double p90 = samples[900] / 1_000.;
                        double p99 = samples[990] / 1_000.;
                        double avg = LongStream.of(samples).average().getAsDouble() / 1000.;
                        System.out.println("B"+"\t"+avg+"\t"+p1 + " " + p10 + " " + p90 + " " + p99);
                        si = 0;
                        iter++;
                    }

                    offsets.clear();
                    for (int i = 0; i < sz; i++) {
                        offsets.add(r.nextLong(0, fileSize - 256));
                    }

                    long st = System.nanoTime();
                    reader.read(segments, offsets);
                    long et = System.nanoTime();

                    samples[si++] = et - st;

                }

            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void testBufferedPread(int sz, int iters) {
        try {
            Path largeFile = Path.of("/home/vlofgren/largefile.dat");
            long fileSize = Files.size(largeFile);

            Random r = new Random();
            List<MemorySegment> segments = new ArrayList<>();
            for (int i = 0; i < sz; i++) {
                segments.add(Arena.ofAuto().allocate(r.nextInt(24, 256)));
            }
            List<Long> offsets = new ArrayList<>();

            long[] samples = new long[1000];
            int si = 0;

            int fd = -1;
            try {
                fd = LinuxSystemCalls.openBuffered(largeFile);
                LinuxSystemCalls.fadviseRandom(fd);

                for (int iter = 0; iter < iters; ) {
                    if (si == samples.length) {
                        Arrays.sort(samples);
                        double p1 = samples[10] / 1_000.;
                        double p10 = samples[100] / 1_000.;
                        double p90 = samples[900] / 1_000.;
                        double p99 = samples[990] / 1_000.;
                        double avg = LongStream.of(samples).average().getAsDouble() / 1000.;
                        System.out.println("BP"+"\t"+avg+"\t"+p1 + " " + p10 + " " + p90 + " " + p99);
                        si = 0;
                        iter++;
                    }

                    offsets.clear();
                    for (int i = 0; i < sz; i++) {
                        offsets.add(r.nextLong(0, fileSize - 256));
                    }

                    long st = System.nanoTime();
                    for (int i = 0; i < sz; i++) {
                        LinuxSystemCalls.readAt(fd, segments.get(i), offsets.get(i));
                    }
                    long et = System.nanoTime();

                    samples[si++] = et - st;
                }
            }
            finally {
                LinuxSystemCalls.closeFd(fd);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }


    static void testDirect(int blockSize, int sz, int iters) {
        try {
            Path largeFile = Path.of("/home/vlofgren/largefile.dat");
            int fileSizeBlocks = (int) ((Files.size(largeFile) & -blockSize) / blockSize);

            Random r = new Random();
            List<MemorySegment> segments = new ArrayList<>();
            for (int i = 0; i < sz; i++) {
                segments.add(Arena.ofAuto().allocate(blockSize, blockSize));
            }
            List<Long> offsets = new ArrayList<>();

            long[] samples = new long[1000];
            int si = 0;

            try (UringFileReader reader = new UringFileReader(largeFile, true)) {
                for (int iter = 0; iter < iters; ) {
                    if (si == samples.length) {
                        Arrays.sort(samples);
                        double p1 = samples[10] / 1_000.;
                        double p10 = samples[100] / 1_000.;
                        double p90 = samples[900] / 1_000.;
                        double p99 = samples[990] / 1_000.;
                        double avg = LongStream.of(samples).average().getAsDouble() / 1000.;
                        System.out.println("DN"+blockSize+"\t"+avg+"\t"+p1 + " " + p10 + " " + p90 + " " + p99);
                        si = 0;
                        iters++;
                    }

                    offsets.clear();
                    for (int i = 0; i < sz; i++) {
                        offsets.add(blockSize * r.nextLong(0, fileSizeBlocks));
                    }

                    long st = System.nanoTime();
                    reader.read(segments, offsets);
                    long et = System.nanoTime();

                    samples[si++] = et - st;

                }

            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }


    static void testDirect1(int blockSize, int iters) {
        try {
            Path largeFile = Path.of("/home/vlofgren/largefile.dat");
            int fileSizeBlocks = (int) ((Files.size(largeFile) & -blockSize) / blockSize);

            Random r = new Random();
            MemorySegment segment = Arena.global().allocate(blockSize, blockSize);

            long[] samples = new long[1000];
            int si = 0;

            int fd = LinuxSystemCalls.openDirect(largeFile);
            if (fd < 0) {
                throw new IOException("open failed");
            }
            try {
                for (int iter = 0; iter < iters; ) {
                    if (si == samples.length) {
                        Arrays.sort(samples);
                        double p1 = samples[10] / 1_000.;
                        double p10 = samples[100] / 1_000.;
                        double p90 = samples[900] / 1_000.;
                        double p99 = samples[990] / 1_000.;
                        double avg = LongStream.of(samples).average().getAsDouble() / 1000.;
                        System.out.println("D1"+blockSize+"\t"+avg+"\t"+p1 + " " + p10 + " " + p90 + " " + p99);
                        si = 0;
                        iters++;
                    }


                    long st = System.nanoTime();
                    int ret;
                    long readOffset = blockSize * r.nextLong(0, fileSizeBlocks);
                    if (blockSize != (ret = LinuxSystemCalls.readAt(fd, segment, readOffset))) {
                        throw new IOException("pread failed: " + ret);
                    }
                    long et = System.nanoTime();

                    samples[si++] = et - st;

                }

            }
            finally {
                LinuxSystemCalls.closeFd(fd);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
//        Thread.ofPlatform().start(() -> testBuffered(128,  32, 65536,1000));
        Thread.ofPlatform().start(() -> testDirect(8192*4, 128,1000));
//        Thread.ofPlatform().start(() -> testBuffered(128, 1000));
//        Thread.ofPlatform().start(() -> testBuffered(128, 1000));
//        Thread.ofPlatform().start(() -> testBuffered(128, 1000));
//        Thread.ofPlatform().start(() -> testBufferedPread(128, 1000));

//        Thread.ofPlatform().start(() -> testDirect1(1024, 1000));
//        Thread.ofPlatform().start(() -> testDirect1(1024, 1000));
//        Thread.ofPlatform().start(() -> testDirect1(1024, 1000));
//        Thread.ofPlatform().start(() -> testDirect1(1024*1024, 1000));
//        Thread.ofPlatform().start(() -> testDirect1(1024*1024, 1000));
//        Thread.ofPlatform().start(() -> testDirect(512, 512,1000));
//        Thread.ofPlatform().start(() -> testDirect(512, 512,1000));
//        Thread.ofPlatform().start(() -> testDirect(512, 512,1000));
//        Thread.ofPlatform().start(() -> testDirect(512, 100));
//        Thread.ofPlatform().start(() -> testDirect(512, 100));
//        Thread.ofPlatform().start(() -> testDirect(512, 100));
//        Thread.ofPlatform().start(() -> testDirect(512, 100));
//        Thread.ofPlatform().start(() -> testBuffered(512, 1000));
//        Thread.ofPlatform().start(() -> testBuffered(512, 1000));
//        Thread.ofPlatform().start(() -> testBuffered(512, 1000));
//        Thread.ofPlatform().start(() -> testBuffered(512, 1000));
//        Thread.ofPlatform().start(() -> testBuffered(100));
//        Thread.ofPlatform().start(() -> testBuffered(100));

        for (;;);
//        testBuffered(100);
    }
}
