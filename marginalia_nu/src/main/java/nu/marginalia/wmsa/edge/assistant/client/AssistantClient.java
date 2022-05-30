package nu.marginalia.wmsa.edge.assistant.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Observable;
import nu.marginalia.wmsa.client.AbstractDynamicClient;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.assistant.dict.DictionaryResponse;
import nu.marginalia.wmsa.edge.assistant.dict.WikiArticles;
import org.eclipse.jetty.util.UrlEncoded;

import java.util.List;

@Singleton
public class AssistantClient extends AbstractDynamicClient {

    @Inject
    public AssistantClient() {
        super(ServiceDescriptor.EDGE_ASSISTANT);
    }

    public Observable<DictionaryResponse> dictionaryLookup(Context ctx, String word) {
        return super.get(ctx,"/dictionary/" + UrlEncoded.encodeString(word), DictionaryResponse.class);
    }

    @SuppressWarnings("unchecked")
    public Observable<List<String>> spellCheck(Context ctx, String word) {
        return (Observable<List<String>>) (Object) super.get(ctx,"/spell-check/" + UrlEncoded.encodeString(word), List.class);
    }
    public Observable<String> unitConversion(Context ctx, String value, String from, String to) {
        return super.get(ctx,"/unit-conversion?value="+value + "&from="+from+"&to="+to);
    }

    public Observable<String> evalMath(Context ctx, String expression) {
        return super.get(ctx,"/eval-expression?value="+UrlEncoded.encodeString(expression));
    }
}
