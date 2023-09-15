package nu.marginalia.io.processed;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ProcessedDataFileNames {
    public static Path documentFileName(Path base, int batchNumber) {
        return base.resolve(String.format("document%04d.parquet", batchNumber));
    }
    public static Path domainFileName(Path base, int batchNumber) {
        return base.resolve(String.format("domain%04d.parquet", batchNumber));
    }
    public static Path domainLinkFileName(Path base, int batchNumber) {
        return base.resolve(String.format("domain-link%04d.parquet", batchNumber));
    }

    public static List<Path> listDocumentFiles(Path base, int untilBatch) {
        List<Path> ret = new ArrayList<>(untilBatch);

        for (int i = 0; i < untilBatch; i++) {
            Path maybe = documentFileName(base, i);
            if (Files.exists(maybe)) {
                ret.add(maybe);
            }
        }

        return ret;
    }

    public static List<Path> listDomainFiles(Path base, int untilBatch) {
        List<Path> ret = new ArrayList<>(untilBatch);

        for (int i = 0; i < untilBatch; i++) {
            Path maybe = domainFileName(base, i);
            if (Files.exists(maybe)) {
                ret.add(maybe);
            }
        }

        return ret;
    }

    public static List<Path> listDomainFiles(Path base) {
        List<Path> ret = new ArrayList<>();

        for (int i = 0;; i++) {
            Path maybe = domainFileName(base, i);
            if (Files.exists(maybe)) {
                ret.add(maybe);
            }
            else {
                break;
            }
        }

        return ret;
    }

    public static List<Path> listDomainLinkFiles(Path base, int untilBatch) {
        List<Path> ret = new ArrayList<>(untilBatch);

        for (int i = 0; i < untilBatch; i++) {
            Path maybe = domainLinkFileName(base, i);
            if (Files.exists(maybe)) {
                ret.add(maybe);
            }
        }

        return ret;
    }
}
