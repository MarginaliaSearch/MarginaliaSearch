package nu.marginalia.api.searchquery;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
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

    public static RpcQueryTerms convertRpcQuery(SearchQuery searchQuery) {
        var subqueryBuilder =
                RpcQueryTerms.newBuilder()
                        .setCompiledQuery(searchQuery.compiledQuery)
                        .addAllTermsQuery(searchQuery.getSearchTermsInclude())
                        .addAllTermsRequire(searchQuery.getSearchTermsAdvice())
                        .addAllTermsExclude(searchQuery.getSearchTermsExclude())
                        .addAllTermsPriority(searchQuery.getSearchTermsPriority())
                        .addAllTermsPriorityWeight(searchQuery.searchTermsPriorityWeight)
                    ;

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
