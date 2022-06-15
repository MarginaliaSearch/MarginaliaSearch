package nu.marginalia.wmsa.edge.index.service.query;

import java.util.stream.LongStream;

public interface Query {
    Query also(int wordId);
    Query not(int wordId);

    LongStream stream();
}
