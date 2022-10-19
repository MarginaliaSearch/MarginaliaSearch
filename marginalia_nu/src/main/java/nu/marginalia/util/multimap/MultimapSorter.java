package nu.marginalia.util.multimap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static nu.marginalia.util.multimap.MultimapFileLong.WORD_SIZE;

public class MultimapSorter {
    private final Path tmpFileDir;
    private final MultimapFileLongSlice multimapFileLong;
    private final LongBuffer buffer;
    private final int internalSortLimit;
    private final int wordSize;

    private static final Logger logger = LoggerFactory.getLogger(MultimapSorter.class);

    public MultimapSorter(MultimapFileLongSlice multimapFileLong, Path tmpFileDir, int internalSortLimit, int wordSize) {
        this.multimapFileLong = multimapFileLong;
        this.tmpFileDir = tmpFileDir;
        this.internalSortLimit = internalSortLimit;
        this.wordSize = wordSize;
        buffer = ByteBuffer.allocateDirect(internalSortLimit * wordSize * 8).asLongBuffer();
    }

    public void sortRange(long start, long end) throws IOException {
        if (end - start < internalSortLimit) {
            quickSortLH(start, end - wordSize);
        }
        else {
            mergeSort(start, (int) (end - start));
        }

        for (long lp = start + wordSize; lp < end; lp += wordSize) {
            if (multimapFileLong.get(lp - wordSize) > multimapFileLong.get(lp)) {

                logger.error("Sort contract breached [{}:{} ({}), ws={}, <isl={}, bc={}]",
                        start, end,
                        end - start,
                        wordSize, end - start < internalSortLimit,
                        buffer.capacity());

            }
        }
    }

    public void mergeSort(long start, int lengthLongs) throws IOException {
        if (lengthLongs == 1)
            return;

        if (lengthLongs < buffer.capacity()) {
            mergeSort(start, lengthLongs, buffer);
        }
        else {
            Path tmpFile = Files.createTempFile(tmpFileDir,"sort-"+start+"-"+(start+lengthLongs), ".dat");
            try (var raf = new RandomAccessFile(tmpFile.toFile(), "rw"); var channel = raf.getChannel()) {
                var workBuffer =
                        channel.map(FileChannel.MapMode.READ_WRITE, 0, wordSize * lengthLongs * WORD_SIZE)
                                .asLongBuffer();
                mergeSort(start, lengthLongs, workBuffer);
            }
            finally {
                tmpFile.toFile().delete();
            }
        }
    }
    private void mergeSort(long start, int length, LongBuffer workBuffer) throws IOException {
        int width = Math.min(Integer.highestOneBit(length), Integer.highestOneBit(buffer.capacity()));

        // Do in-memory sorting up until internalSortLimit first
        for (int i = 0; i < length; i += width) {
            quickSort(start + i, Math.min(width, length-i));
        }

        // Then finish with merge sort
        for (; width < length; width*=2) {

            for (int i = 0; i < length; i += 2*width) {
                merge(start, i, Math.min(i+width, length), Math.min(i+2*width, length), workBuffer);
            }

            workBuffer.clear();
            multimapFileLong.write(workBuffer, length, start);
        }

    }


    void merge(long offset, int left, int right, int end, LongBuffer workBuffer) {
        long idxL = left;
        long idxR = right;

        for (int putPos = left; putPos < end; putPos+= wordSize) {
            final long bufferL = multimapFileLong.get(offset+idxL);
            final long bufferR = multimapFileLong.get(offset+idxR);

            if (idxL < right && (idxR >= end || bufferL < bufferR)) {
                workBuffer.put(putPos, bufferL);
                for (int s = 1; s < wordSize; s++) {
                    workBuffer.put(putPos + s, multimapFileLong.get(offset + idxL + s));
                }
                idxL+= wordSize;
            }
            else {
                workBuffer.put(putPos, bufferR);
                for (int s = 1; s < wordSize; s++) {
                    workBuffer.put(putPos + s, multimapFileLong.get(offset + idxR + s));
                }
                idxR+= wordSize;
            }
        }
    }

    public void insertionSort(long start, int n) {
        multimapFileLong.insertionSort(wordSize, start, n);
    }

    private void swap(long a, long b) {
        multimapFileLong.swapn(wordSize, a, b);
    }

    public void quickSort(long start, long length) {
        quickSortLH(start, start + length - wordSize);

    }
    public void quickSortLH(long low, long highInclusive) {

        if (low >= 0 && highInclusive >= 0 && low < highInclusive) {

            if (highInclusive - low < 32) {
                multimapFileLong.insertionSort(wordSize, low, (int) (1 + (highInclusive - low) / wordSize));
            }
            else {
                long p = multimapFileLong.quickSortPartition(wordSize, low, highInclusive);

                quickSortLH(low, p);
                quickSortLH(p + wordSize, highInclusive);
            }
        }
    }

}
