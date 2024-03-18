package nu.marginalia.functions.searchquery.query_parser.variant;

import nu.marginalia.functions.searchquery.query_parser.variant.model.QWordGraph;

public interface ExpansionStrategy {
    void expand(QWordGraph graph);
}
