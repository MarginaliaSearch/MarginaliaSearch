package nu.marginalia.wmsa.edge.model.search;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@ToString
@Getter
@AllArgsConstructor
public class EdgeSearchSubquery {

    public final List<String> searchTermsInclude;
    public final List<String> searchTermsExclude;
    public final List<String> searchTermsAdvice;
    public final List<String> searchTermsPriority;

    private double value = 0;

    public EdgeSearchSubquery(List<String> searchTermsInclude,
                              List<String> searchTermsExclude,
                              List<String> searchTermsAdvice,
                              List<String> searchTermsPriority
                              ) {
        this.searchTermsInclude = searchTermsInclude;
        this.searchTermsExclude = searchTermsExclude;
        this.searchTermsAdvice = searchTermsAdvice;
        this.searchTermsPriority = searchTermsPriority;
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
