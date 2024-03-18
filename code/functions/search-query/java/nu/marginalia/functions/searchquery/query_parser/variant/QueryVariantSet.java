package nu.marginalia.functions.searchquery.query_parser.variant;

import lombok.Getter;
import lombok.ToString;
import nu.marginalia.functions.searchquery.query_parser.token.Token;

import java.util.ArrayList;
import java.util.List;

@Getter
@ToString
public class QueryVariantSet {
    public final List<QueryVariant> faithful = new ArrayList<>();
    public final List<QueryVariant> alternative = new ArrayList<>();

    public final List<Token> nonLiterals = new ArrayList<>();

    public boolean isEmpty() {
        return faithful.isEmpty() && alternative.isEmpty() && nonLiterals.isEmpty();
    }
}
