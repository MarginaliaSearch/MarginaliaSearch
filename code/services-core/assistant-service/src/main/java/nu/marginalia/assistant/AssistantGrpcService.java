package nu.marginalia.assistant;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import nu.marginalia.assistant.api.*;
import nu.marginalia.assistant.dict.DictionaryService;
import nu.marginalia.assistant.dict.SpellChecker;
import nu.marginalia.assistant.domains.DomainInformationService;
import nu.marginalia.assistant.domains.SimilarDomainsService;
import nu.marginalia.assistant.eval.MathParser;
import nu.marginalia.assistant.eval.Units;

public class AssistantGrpcService extends AssistantApiGrpc.AssistantApiImplBase {

    private final DictionaryService dictionaryService;
    private final SpellChecker spellChecker;
    private final Units units;
    private final MathParser mathParser;
    private final DomainInformationService domainInformationService;
    private final SimilarDomainsService similarDomainsService;
    @Inject
    public AssistantGrpcService(DictionaryService dictionaryService,
                                SpellChecker spellChecker, Units units, MathParser mathParser, DomainInformationService domainInformationService, SimilarDomainsService similarDomainsService)
    {

        this.dictionaryService = dictionaryService;
        this.spellChecker = spellChecker;
        this.units = units;
        this.mathParser = mathParser;
        this.domainInformationService = domainInformationService;
        this.similarDomainsService = similarDomainsService;
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

    @Override
    public void getDomainInfo(RpcDomainId request, StreamObserver<RpcDomainInfoResponse> responseObserver) {
        var ret = domainInformationService.domainInfo(request.getDomainId());

        ret.ifPresent(responseObserver::onNext);

        responseObserver.onCompleted();
    }

    @Override
    public void getSimilarDomains(RpcDomainLinksRequest request,
                                  StreamObserver<RpcSimilarDomains> responseObserver) {
        var ret = similarDomainsService.getSimilarDomains(request.getDomainId(), request.getCount());

        var responseBuilder = RpcSimilarDomains
                .newBuilder()
                .addAllDomains(ret);

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getLinkingDomains(RpcDomainLinksRequest request, StreamObserver<RpcSimilarDomains> responseObserver) {
        var ret = similarDomainsService.getLinkingDomains(request.getDomainId(), request.getCount());

        var responseBuilder = RpcSimilarDomains
                .newBuilder()
                .addAllDomains(ret);

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }
}
