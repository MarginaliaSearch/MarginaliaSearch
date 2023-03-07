package nu.marginalia.index.client.model.query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.stream.Collectors;

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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (!searchTermsInclude.isEmpty()) sb.append("include=").append(searchTermsInclude.stream().collect(Collectors.joining(",", "[", "] ")));
        if (!searchTermsExclude.isEmpty()) sb.append("exclude=").append(searchTermsExclude.stream().collect(Collectors.joining(",", "[", "] ")));
        if (!searchTermsAdvice.isEmpty()) sb.append("advice=").append(searchTermsAdvice.stream().collect(Collectors.joining(",", "[", "] ")));
        if (!searchTermsPriority.isEmpty()) sb.append("priority=").append(searchTermsPriority.stream().collect(Collectors.joining(",", "[", "] ")));

        return sb.toString();
    }


}
