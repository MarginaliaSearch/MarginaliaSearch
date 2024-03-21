package nu.marginalia.functions.searchquery.query_parser;

import nu.marginalia.functions.searchquery.query_parser.model.QWordGraph;

public interface ExpansionStrategy {
    void expand(QWordGraph graph);
}
