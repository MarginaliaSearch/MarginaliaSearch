package nu.marginalia.functions.searchquery.query_parser.variant.strategy;

import nu.marginalia.functions.searchquery.query_parser.variant.QueryWord;
import nu.marginalia.functions.searchquery.query_parser.variant.VariantStrategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Variant strategy that combines word that have dashes, as sometimes lawn-chair
 * gets spelled lawnchair  */
public class CombineDashes implements VariantStrategy {
    final Pattern dashBoundary = Pattern.compile("-");

    public CombineDashes() {
    }

    @Override
    public Collection<? extends List<String>> constructVariants(List<QueryWord> words) {
        List<String> asTokens2 = new ArrayList<>();
        boolean dash = false;

        for (var span : words) {
            var matcher = dashBoundary.matcher(span.word);
            if (matcher.find()) {
                String combined = dashBoundary.matcher(span.word).replaceAll("");
                asTokens2.add(combined);
            }

            asTokens2.add(span.word);
        }

        if (dash) {
            return List.of(asTokens2);
        }
        return Collections.emptyList();
    }
}
