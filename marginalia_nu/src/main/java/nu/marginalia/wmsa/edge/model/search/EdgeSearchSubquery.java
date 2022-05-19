package nu.marginalia.wmsa.edge.model.search;

import lombok.*;
import lombok.experimental.FieldNameConstants;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import org.h2.index.Index;

import java.util.List;

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
        this.termSize =  (int) searchTermsInclude.stream().flatMapToInt(String::chars).filter(i -> '_'==i).count();
    }

    public EdgeSearchSubquery withBlock(IndexBlock block) {
        return new EdgeSearchSubquery(searchTermsInclude, searchTermsExclude, block);
    }

    public int termSize() {
        return termSize;
    }
}
