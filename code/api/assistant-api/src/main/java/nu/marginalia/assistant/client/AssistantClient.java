package nu.marginalia.assistant.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.assistant.api.AssistantApiGrpc;
import nu.marginalia.assistant.client.model.DictionaryResponse;
import nu.marginalia.assistant.client.model.DomainInformation;
import nu.marginalia.assistant.client.model.SimilarDomain;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcSingleNodeChannelPool;
import nu.marginalia.service.id.ServiceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static nu.marginalia.assistant.client.AssistantProtobufCodec.*;

@Singleton
public class AssistantClient {
    private static final Logger logger = LoggerFactory.getLogger(AssistantClient.class);

    private final GrpcSingleNodeChannelPool<AssistantApiGrpc.AssistantApiBlockingStub> channelPool;
    private final ExecutorService virtualExecutorService = Executors.newVirtualThreadPerTaskExecutor();
    @Inject
    public AssistantClient(GrpcChannelPoolFactory factory) {
        this.channelPool = factory.createSingle(ServiceId.Assistant, AssistantApiGrpc::newBlockingStub);

    }

    public Future<DictionaryResponse> dictionaryLookup(String word) {
        return virtualExecutorService.submit(() -> {
            var rsp = channelPool.api().dictionaryLookup(
                    DictionaryLookup.createRequest(word)
            );

            return DictionaryLookup.convertResponse(rsp);
        });
    }

    @SuppressWarnings("unchecked")
    public Future<List<String>> spellCheck(String word) {
        return virtualExecutorService.submit(() -> {
            var rsp = channelPool.api().spellCheck(
                SpellCheck.createRequest(word)
            );

            return SpellCheck.convertResponse(rsp);
        });
    }

    public Map<String, List<String>> spellCheck(List<String> words, Duration timeout) throws InterruptedException {
        List<Callable<Map.Entry<String, List<String>>>> tasks = new ArrayList<>();

        for (String w : words) {
            tasks.add(() -> {
                var rsp = channelPool.api().spellCheck(
                        SpellCheck.createRequest(w)
                );

                return Map.entry(w, SpellCheck.convertResponse(rsp));
            });
        }

        var futures = virtualExecutorService.invokeAll(tasks, timeout.toMillis(), TimeUnit.MILLISECONDS);
        Map<String, List<String>> results = new HashMap<>();

        for (var f : futures) {
            if (!f.isDone())
                continue;

            var entry = f.resultNow();

            results.put(entry.getKey(), entry.getValue());
        }

        return results;
    }

    public Future<String> unitConversion(String value, String from, String to) {
        return virtualExecutorService.submit(() -> {
            var rsp = channelPool.api().unitConversion(
                    UnitConversion.createRequest(from, to, value)
            );

            return UnitConversion.convertResponse(rsp);
        });
    }

    public Future<String> evalMath(String expression) {
        return virtualExecutorService.submit(() -> {
            var rsp = channelPool.api().evalMath(
                    EvalMath.createRequest(expression)
            );

            return EvalMath.convertResponse(rsp);
        });
    }

    public Future<List<SimilarDomain>> similarDomains(int domainId, int count) {
        return virtualExecutorService.submit(() -> {
            try {
                var rsp = channelPool.api().getSimilarDomains(
                        DomainQueries.createRequest(domainId, count)
                );

                return DomainQueries.convertResponse(rsp);
            }
            catch (Exception e) {
                logger.warn("Failed to get similar domains", e);

                throw e;
            }
        });
    }

    public Future<List<SimilarDomain>> linkedDomains(int domainId, int count) {
        return virtualExecutorService.submit(() -> {
            try {
                var rsp = channelPool.api().getLinkingDomains(
                        DomainQueries.createRequest(domainId, count)
                );

                return DomainQueries.convertResponse(rsp);
            }
            catch (Exception e) {
                logger.warn("Failed to get linked domains", e);
                throw e;
            }
        });

    }

    public Future<DomainInformation> domainInformation(int domainId) {
        return virtualExecutorService.submit(() -> {
            try {
                var rsp = channelPool.api().getDomainInfo(
                        DomainInfo.createRequest(domainId)
                );

                return DomainInfo.convertResponse(rsp);
            }
            catch (Exception e) {
                logger.warn("Failed to get domain information", e);

                throw e;
            }
        });
    }

    public boolean isAccepting() {
        return channelPool.hasChannel();
    }
}
