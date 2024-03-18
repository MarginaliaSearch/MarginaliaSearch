package nu.marginalia.functions.searchquery.query_parser.variant;

import java.util.Collection;
import java.util.List;

public interface VariantStrategy {
    Collection<? extends List<String>> constructVariants(List<QueryWord> ls);
}
