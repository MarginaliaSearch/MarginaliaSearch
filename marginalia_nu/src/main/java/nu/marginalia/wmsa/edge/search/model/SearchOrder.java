package nu.marginalia.wmsa.edge.search.model;

import nu.marginalia.wmsa.edge.index.model.IndexBlock;

import java.util.List;

class SearchOrder {
    static List<IndexBlock> DEFAULT_ORDER
            = List.of(IndexBlock.Title, IndexBlock.Words_1, IndexBlock.Words_2, IndexBlock.Words_4, IndexBlock.Words_8, IndexBlock.Words_16Plus);
}
