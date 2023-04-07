package nu.marginalia.search.query;

import nu.marginalia.index.client.model.query.SearchSubquery;
import nu.marginalia.query_parser.token.Token;
import nu.marginalia.query_parser.token.TokenVisitor;
import nu.marginalia.search.model.SearchProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class QuerySearchTermsAccumulator implements TokenVisitor {
    public List<String> searchTermsExclude = new ArrayList<>();
    public List<String> searchTermsInclude = new ArrayList<>();
    public List<String> searchTermsAdvice = new ArrayList<>();
    public List<String> searchTermsPriority = new ArrayList<>();

    public String near;
    public String domain;

    public SearchSubquery createSubquery() {
        return new SearchSubquery(searchTermsInclude, searchTermsExclude, searchTermsAdvice, searchTermsPriority);
    }

    public QuerySearchTermsAccumulator(SearchProfile profile, List<Token> parts) {
        near = profile.getNearDomain();

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
        if (parts.length > 1) {
            searchTermsAdvice.add(token.str);
            searchTermsInclude.addAll(Arrays.asList(parts));
        }
        else {
            searchTermsInclude.add(token.str);
        }
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
    public void onNearTerm(Token token) {
        near = token.str;
    }

    @Override
    public void onYearTerm(Token token) {

    }

    @Override
    public void onSizeTerm(Token token) {

    }

    @Override
    public void onRankTerm(Token token) {

    }

    @Override
    public void onQualityTerm(Token token) {

    }

    @Override
    public void onQsTerm(Token token) {

    }
}
