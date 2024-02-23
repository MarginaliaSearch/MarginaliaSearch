package nu.marginalia.api.math;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.service.client.GrpcChannelPoolFactory;
import nu.marginalia.service.client.GrpcSingleNodeChannelPool;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import nu.marginalia.api.math.model.*;
import nu.marginalia.api.math.MathProtobufCodec.*;


@Singleton
public class MathClient {
    private static final Logger logger = LoggerFactory.getLogger(MathClient.class);

    private final GrpcSingleNodeChannelPool<MathApiGrpc.MathApiBlockingStub> channelPool;
    private final ExecutorService virtualExecutorService = Executors.newVirtualThreadPerTaskExecutor();
    @Inject
    public MathClient(GrpcChannelPoolFactory factory) {
        this.channelPool = factory.createSingle(
                ServiceKey.forGrpcApi(MathApiGrpc.class, ServicePartition.any()),
                MathApiGrpc::newBlockingStub);

    }

    public Future<DictionaryResponse> dictionaryLookup(String word) {
        return channelPool.call(MathApiGrpc.MathApiBlockingStub::dictionaryLookup)
                .async(virtualExecutorService)
                .run(DictionaryLookup.createRequest(word))
                .thenApply(DictionaryLookup::convertResponse);
    }

    @SuppressWarnings("unchecked")
    public Future<List<String>> spellCheck(String word) {
        return channelPool.call(MathApiGrpc.MathApiBlockingStub::spellCheck)
                .async(virtualExecutorService)
                .run(SpellCheck.createRequest(word))
                .thenApply(SpellCheck::convertResponse);
    }

    public Map<String, List<String>> spellCheck(List<String> words, Duration timeout) throws InterruptedException {
        List<RpcSpellCheckRequest> requests = words.stream().map(SpellCheck::createRequest).toList();

        var future = channelPool.call(MathApiGrpc.MathApiBlockingStub::spellCheck)
                .async(virtualExecutorService)
                .runFor(requests);

        try {
            var results = future.get();
            Map<String, List<String>> map = new HashMap<>();
            for (int i = 0; i < words.size(); i++) {
                map.put(words.get(i), SpellCheck.convertResponse(results.get(i)));
            }
            return map;
        }
        catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public Future<String> unitConversion(String value, String from, String to) {
        return channelPool.call(MathApiGrpc.MathApiBlockingStub::unitConversion)
                .async(virtualExecutorService)
                .run(UnitConversion.createRequest(from, to, value))
                .thenApply(UnitConversion::convertResponse);
    }

    public Future<String> evalMath(String expression) {
        return channelPool.call(MathApiGrpc.MathApiBlockingStub::evalMath)
                .async(virtualExecutorService)
                .run(EvalMath.createRequest(expression))
                .thenApply(EvalMath::convertResponse);
    }

    public boolean isAccepting() {
        return channelPool.hasChannel();
    }
}
