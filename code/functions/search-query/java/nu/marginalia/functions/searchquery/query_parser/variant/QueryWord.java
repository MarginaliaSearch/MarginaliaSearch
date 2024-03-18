package nu.marginalia.functions.searchquery.query_parser.variant;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class QueryWord {
    public final String stemmed;
    public final String word;
    public final String wordOriginal;
}
