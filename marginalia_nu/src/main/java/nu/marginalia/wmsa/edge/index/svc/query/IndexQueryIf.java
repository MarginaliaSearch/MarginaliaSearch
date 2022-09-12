package nu.marginalia.wmsa.edge.index.svc.query;

import java.util.stream.LongStream;

public interface IndexQueryIf {
    IndexQueryIf also(int wordId);
    IndexQueryIf alsoCached(int wordId);

    IndexQueryIf not(int wordId);

    LongStream stream();
}
