package nu.marginalia.wmsa.edge.search;

import nu.marginalia.wmsa.edge.index.model.IndexBlock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public enum EdgeSearchProfile {
    DEFAULT("default",
            Collections.emptyList(),
            List.of(IndexBlock.TitleKeywords, IndexBlock.Title, IndexBlock.Top, IndexBlock.Middle, IndexBlock.Low, IndexBlock.Link, IndexBlock.Words, IndexBlock.NamesWords),
            0, 1),
    MODERN("modern",
            Collections.emptyList(),
            List.of(IndexBlock.TitleKeywords, IndexBlock.Title, IndexBlock.Top, IndexBlock.Middle, IndexBlock.Low, IndexBlock.Link, IndexBlock.Words, IndexBlock.NamesWords),
            2),
    CORPO("corpo",
            Collections.emptyList(),
            List.of(IndexBlock.TitleKeywords, IndexBlock.Title, IndexBlock.Top, IndexBlock.Middle, IndexBlock.Low, IndexBlock.Link,  IndexBlock.Words, IndexBlock.NamesWords),
            4, 5, 6, 7),
    YOLO("yolo",
            Collections.emptyList(),
            List.of(IndexBlock.TitleKeywords, IndexBlock.Title, IndexBlock.Top, IndexBlock.Middle, IndexBlock.Low, IndexBlock.Link, IndexBlock.Words, IndexBlock.NamesWords),
            0, 2, 1, 3, 4, 6),
    CORPO_CLEAN("corpo-clean",
            Collections.emptyList(),
            List.of(IndexBlock.TitleKeywords, IndexBlock.Title, IndexBlock.Top, IndexBlock.Middle, IndexBlock.Low, IndexBlock.Link, IndexBlock.Words, IndexBlock.NamesWords),
            4, 5),
    ACADEMIA("academia",
            Collections.emptyList(),
            List.of(IndexBlock.TitleKeywords, IndexBlock.Title, IndexBlock.Top, IndexBlock.Middle, IndexBlock.Low, IndexBlock.Link,  IndexBlock.Words, IndexBlock.NamesWords),
            3),
    ;


    public final String name;
    public final List<String> additionalSearchTerm;
    public final List<Integer> buckets;
    public final List<IndexBlock> indexBlocks;

    EdgeSearchProfile(String name,
                      List<String> additionalSearchTerm,
                      List<IndexBlock> indexBlocks,
                      int... buckets) {
        this.name = name;
        this.additionalSearchTerm = additionalSearchTerm;
        this.indexBlocks = indexBlocks;
        this.buckets = Arrays.stream(buckets).boxed().collect(Collectors.toList());
    }

    static EdgeSearchProfile getSearchProfile(String param) {
        if (null == param) {
            return YOLO;
        }
        return switch (param) {
            case "modern" -> MODERN;
            case "default" -> DEFAULT;
            case "corpo" -> CORPO;
            case "academia" -> ACADEMIA;
            default -> YOLO;
        };
    }
}
