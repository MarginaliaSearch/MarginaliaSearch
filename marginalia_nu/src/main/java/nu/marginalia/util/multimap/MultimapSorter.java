package nu.marginalia.util.multimap;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static nu.marginalia.util.multimap.MultimapFileLong.WORD_SIZE;

public class MultimapSorter {
    private final Path tmpFileDir;
    private final int internalSortLimit;
    private final MultimapFileLongSlice multimapFileLong;
    private final long[] buffer;

    public MultimapSorter(MultimapFileLongSlice multimapFileLong, Path tmpFileDir, int internalSortLimit) {
        this.multimapFileLong = multimapFileLong;
        this.tmpFileDir = tmpFileDir;
        this.internalSortLimit = internalSortLimit;
        buffer = new long[internalSortLimit];
    }

    public void sort(long start, int length) throws IOException {
        if (length <= internalSortLimit) {
            multimapFileLong.read(buffer, length, start);
            Arrays.sort(buffer, 0, length);
            multimapFileLong.write(buffer, length, start);
        }
        else {
            externalSort(start, length);
        }
    }


    private void externalSort(long start, int length) throws IOException {
        Path tmpFile = Files.createTempFile(tmpFileDir,"sort-"+start+"-"+(start+length), ".dat");

        try (var raf = new RandomAccessFile(tmpFile.toFile(), "rw"); var channel = raf.getChannel()) {
            var workBuffer =
                    channel.map(FileChannel.MapMode.READ_WRITE, 0,  length * WORD_SIZE)
                            .asLongBuffer();

            int width = Math.min(Integer.highestOneBit(length), Integer.highestOneBit(internalSortLimit));

            // Do in-memory sorting up until internalSortLimit first
            for (int i = 0; i < length; i += width) {
                sort(start + i, Math.min(width, length-i));
            }

            // Then merge sort on disk for the rest
            for (; width < length; width*=2) {

                for (int i = 0; i < length; i += 2*width) {
                    merge(start, i, Math.min(i+width, length), Math.min(i+2*width, length), workBuffer);
                }

                workBuffer.clear();
                multimapFileLong.write(workBuffer, start);
            }

        }
        finally {
            tmpFile.toFile().delete();
        }
    }

    void merge(long offset, int left, int right, int end, LongBuffer workBuffer) {
        int i = left;
        int j = right;

        for (int k = left; k < end; k++) {
            final long bufferI = multimapFileLong.get(offset+i);
            final long bufferJ = multimapFileLong.get(offset+j);

            if (i < right && (j >= end || bufferI < bufferJ)) {
                workBuffer.put(k, bufferI);
                i++;
            }
            else {
                workBuffer.put(k, bufferJ);
                j++;
            }
        }
    }

}
