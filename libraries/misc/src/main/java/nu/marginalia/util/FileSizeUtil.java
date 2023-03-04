package nu.marginalia.util;

public class FileSizeUtil {
    public static String readableSize(long byteCount) {
        if (byteCount < 1024L) {
            return String.format("%db", byteCount);
        }
        if (byteCount < 1024*1024L) {
            return String.format("%2.2fKb", byteCount/1024.);
        }
        if (byteCount < 1024*1024*1024L) {
            return String.format("%2.2fMb", byteCount/1024/1024.);
        }

        return String.format("%2.2fGb", byteCount/1024/1024L/1024.);

    }
}
