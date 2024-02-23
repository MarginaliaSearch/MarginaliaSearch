package nu.marginalia.functions.math;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import nu.marginalia.api.math.*;
import nu.marginalia.functions.math.dict.DictionaryService;
import nu.marginalia.functions.math.dict.SpellChecker;
import nu.marginalia.functions.math.eval.MathParser;
import nu.marginalia.functions.math.eval.Units;

public class MathGrpcService extends MathApiGrpc.MathApiImplBase {

    private final DictionaryService dictionaryService;
    private final SpellChecker spellChecker;
    private final Units units;
    private final MathParser mathParser;

    @Inject
    public MathGrpcService(DictionaryService dictionaryService, SpellChecker spellChecker, Units units, MathParser mathParser)
    {

        this.dictionaryService = dictionaryService;
        this.spellChecker = spellChecker;
        this.units = units;
        this.mathParser = mathParser;
    }

    @Override
    public void dictionaryLookup(RpcDictionaryLookupRequest request,
                                 StreamObserver<RpcDictionaryLookupResponse> responseObserver)
    {
        var definition = dictionaryService.define(request.getWord());

        var responseBuilder = RpcDictionaryLookupResponse
                .newBuilder()
                .setWord(request.getWord());

        for (var def : definition.entries) {
            responseBuilder.addEntries(
                    RpcDictionaryEntry.newBuilder()
                            .setWord(def.word)
                            .setDefinition(def.definition)
                            .setType(def.type)
            );
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void spellCheck(RpcSpellCheckRequest request,
                           StreamObserver<RpcSpellCheckResponse> responseObserver)
    {
        var result = spellChecker.correct(request.getText());
        var response = RpcSpellCheckResponse.newBuilder()
                .addAllSuggestions(result)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void unitConversion(RpcUnitConversionRequest request, StreamObserver<RpcUnitConversionResponse> responseObserver) {
        var res = units.convert(request.getUnit(),
                request.getFrom(),
                request.getTo());

        res.ifPresent(s -> {
            var response = RpcUnitConversionResponse.newBuilder()
                    .setResult(s)
                    .build();
            responseObserver.onNext(response);

        });

        responseObserver.onCompleted();
    }

    @Override
    public void evalMath(RpcEvalMathRequest request, StreamObserver<RpcEvalMathResponse> responseObserver) {
        var ret = mathParser.eval(request.getExpression());

        responseObserver.onNext(RpcEvalMathResponse.newBuilder()
                .setResult(Double.toString(ret))
                .build());

        responseObserver.onCompleted();
    }

}
