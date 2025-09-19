package nu.marginalia.api.searchquery;

import nu.marginalia.api.searchquery.model.query.SearchPhraseConstraint;
import nu.marginalia.api.searchquery.model.query.SearchQuery;
import nu.marginalia.api.searchquery.model.query.SpecificationLimit;
import nu.marginalia.api.searchquery.model.query.SpecificationLimitType;

import java.util.ArrayList;
import java.util.List;

public class IndexProtobufCodec {

    public static SpecificationLimit convertSpecLimit(RpcSpecLimit limit) {
        return new SpecificationLimit(
                SpecificationLimitType.valueOf(limit.getType().name()),
                limit.getValue()
        );
    }

    public static RpcSpecLimit convertSpecLimit(SpecificationLimit limit) {
        return RpcSpecLimit.newBuilder()
                .setType(RpcSpecLimit.TYPE.valueOf(limit.type().name()))
                .setValue(limit.value())
                .build();
    }

    public static SearchQuery convertRpcQuery(RpcQuery query) {
        List<SearchPhraseConstraint> phraseConstraints = new ArrayList<>();

        for (int j = 0; j < query.getPhrasesCount(); j++) {
            var coh = query.getPhrases(j);
            if (coh.getType() == RpcPhrases.TYPE.OPTIONAL) {
                phraseConstraints.add(new SearchPhraseConstraint.Optional(List.copyOf(coh.getTermsList())));
            }
            else if (coh.getType() == RpcPhrases.TYPE.MANDATORY) {
                phraseConstraints.add(new SearchPhraseConstraint.Mandatory(List.copyOf(coh.getTermsList())));
            }
            else if (coh.getType() == RpcPhrases.TYPE.FULL) {
                phraseConstraints.add(new SearchPhraseConstraint.Full(List.copyOf(coh.getTermsList())));
            }
            else {
                throw new IllegalArgumentException("Unknown phrase constraint type: " + coh.getType());
            }
        }

        return new SearchQuery(
                query.getCompiledQuery(),
                query.getIncludeList(),
                query.getExcludeList(),
                query.getAdviceList(),
                query.getPriorityList(),
                phraseConstraints
        );
    }

    public static RpcQuery convertRpcQuery(SearchQuery searchQuery) {
        var subqueryBuilder =
                RpcQuery.newBuilder()
                        .setCompiledQuery(searchQuery.compiledQuery)
                        .addAllInclude(searchQuery.getSearchTermsInclude())
                        .addAllAdvice(searchQuery.getSearchTermsAdvice())
                        .addAllExclude(searchQuery.getSearchTermsExclude())
                        .addAllPriority(searchQuery.getSearchTermsPriority());

        for (var constraint : searchQuery.phraseConstraints) {
            switch (constraint) {
                case SearchPhraseConstraint.Optional(List<String> terms) ->
                    subqueryBuilder.addPhrasesBuilder()
                            .addAllTerms(terms)
                            .setType(RpcPhrases.TYPE.OPTIONAL);
                case SearchPhraseConstraint.Mandatory(List<String> terms) ->
                    subqueryBuilder.addPhrasesBuilder()
                            .addAllTerms(terms)
                            .setType(RpcPhrases.TYPE.MANDATORY);
                case SearchPhraseConstraint.Full(List<String> terms) ->
                    subqueryBuilder.addPhrasesBuilder()
                            .addAllTerms(terms)
                            .setType(RpcPhrases.TYPE.FULL);
            }
        }

        return subqueryBuilder.build();
    }

}
