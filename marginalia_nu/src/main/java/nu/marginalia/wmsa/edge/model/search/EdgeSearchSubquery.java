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

    private double value = 0;

    public EdgeSearchSubquery(List<String> searchTermsInclude, List<String> searchTermsExclude, IndexBlock block) {
        this.searchTermsInclude = searchTermsInclude;
        this.searchTermsExclude = searchTermsExclude;
        this.block = block;
    }

    public EdgeSearchSubquery withBlock(IndexBlock block) {
        return new EdgeSearchSubquery(
                new CopyOnWriteArrayList<>(searchTermsInclude),
                new CopyOnWriteArrayList<>(searchTermsExclude),
                block).setValue(value);
    }

    public EdgeSearchSubquery setValue(double value) {
        if (Double.isInfinite(value) || Double.isNaN(value)) {
            this.value = Double.MAX_VALUE;
        } else {
            this.value = value;
        }
        return this;
    }

}
