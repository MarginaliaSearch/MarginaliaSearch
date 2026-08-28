package nu.marginalia.api.searchquery;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.query.SpecificationLimit;
import nu.marginalia.api.searchquery.model.query.SpecificationLimitType;

import java.util.ArrayList;
import java.util.Arrays;
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

    public static CompiledQuery<String> convertCompiledQuery(RpcCompiledQuery query) {
        List<IntList> paths = new ArrayList<>(query.getPathsCount());
        for (RpcTermPath path : query.getPathsList()) {
            paths.add(new IntArrayList(path.getTermList()));
        }

        int[] variantClasses = new int[query.getVariantClassCount()];
        Arrays.setAll(variantClasses, query::getVariantClass);

        String[] terms = query.getTermsList().toArray(String[]::new);

        return new CompiledQuery<>(paths, variantClasses, terms);
    }

    public static RpcCompiledQuery convertCompiledQuery(CompiledQuery<String> query) {
        var builder = RpcCompiledQuery.newBuilder();

        for (int i = 0; i < query.size(); i++) {
            builder.addTerms(query.at(i));
            builder.addVariantClass(query.variantClasses[i]);
        }

        for (IntList path : query.paths) {
            builder.addPaths(RpcTermPath.newBuilder().addAllTerm(path));
        }

        return builder.build();
    }

}
