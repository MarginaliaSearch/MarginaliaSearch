package nu.marginalia.wmsa.edge.model.search;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ToString
@Getter
@AllArgsConstructor
public class EdgeSearchSubquery {

    public final List<String> searchTermsInclude;
    public final List<String> searchTermsExclude;
    public final IndexBlock block;

    private final int termSize;
    public EdgeSearchSubquery(List<String> searchTermsInclude, List<String> searchTermsExclude, IndexBlock block) {
        this.searchTermsInclude = searchTermsInclude;
        this.searchTermsExclude = searchTermsExclude;
        this.block = block;
        this.termSize = (int) searchTermsInclude.stream().flatMapToInt(String::chars).filter(i -> '_'==i).count();
    }

    public EdgeSearchSubquery withBlock(IndexBlock block) {
        return new EdgeSearchSubquery(
                new CopyOnWriteArrayList<>(searchTermsInclude),
                new CopyOnWriteArrayList<>(searchTermsExclude),
                block);
    }

    public int termSize() {
        return termSize;
    }
}
