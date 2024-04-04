package nu.marginalia.functions.searchquery.svc;

import nu.marginalia.api.searchquery.model.query.SearchQuery;
import nu.marginalia.language.WordPatterns;
import nu.marginalia.functions.searchquery.query_parser.token.Token;
import nu.marginalia.functions.searchquery.query_parser.token.TokenVisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** @see SearchQuery */
public class QuerySearchTermsAccumulator implements TokenVisitor {
    public List<String> searchTermsExclude = new ArrayList<>();
    public List<String> searchTermsInclude = new ArrayList<>();
    public List<String> searchTermsAdvice = new ArrayList<>();
    public List<String> searchTermsPriority = new ArrayList<>();
    public List<List<String>> searchTermCoherences = new ArrayList<>();

    public String domain;

    public QuerySearchTermsAccumulator(List<Token> parts) {
        for (Token t : parts) {
            t.visit(this);
        }

        if (searchTermsInclude.isEmpty() && !searchTermsAdvice.isEmpty()) {
            searchTermsInclude.addAll(searchTermsAdvice);
            searchTermsAdvice.clear();
        }

    }

    @Override
    public void onLiteralTerm(Token token) {
        searchTermsInclude.add(token.str);
    }

    @Override
    public void onQuotTerm(Token token) {
        String[] parts = token.str.split("_");

        // HACK (2023-05-02 vlofgren)
        //
        // Checking for stop words here is a bit of a stop-gap to fix the issue of stop words being
        // required in the query (which is a problem because they are not indexed). How to do this
        // in a clean way is a bit of an open problem that may not get resolved until query-parsing is
        // improved.

        if (parts.length > 1 && !anyPartIsStopWord(parts)) {
            // Prefer that the actual n-gram is present
            searchTermsAdvice.add(token.str);

            // Require that the terms appear in the same sentence
            searchTermCoherences.add(Arrays.asList(parts));

            // Require that each term exists in the document
            // (needed for ranking)
            searchTermsInclude.addAll(Arrays.asList(parts));
        }
        else {
            searchTermsInclude.add(token.str);

        }
    }

    private boolean anyPartIsStopWord(String[] parts) {
        for (String part : parts) {
            if (WordPatterns.isStopWord(part)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onExcludeTerm(Token token) {
        searchTermsExclude.add(token.str);
    }

    @Override
    public void onPriorityTerm(Token token) {
        searchTermsPriority.add(token.str);
    }

    @Override
    public void onAdviceTerm(Token token) {
        searchTermsAdvice.add(token.str);

        if (token.str.toLowerCase().startsWith("site:")) {
            domain = token.str.substring("site:".length());
        }
    }

    @Override
    public void onYearTerm(Token token) {}
    @Override
    public void onSizeTerm(Token token) {}
    @Override
    public void onRankTerm(Token token) {}
    @Override
    public void onQualityTerm(Token token) {}
    @Override
    public void onQsTerm(Token token) {}
}
