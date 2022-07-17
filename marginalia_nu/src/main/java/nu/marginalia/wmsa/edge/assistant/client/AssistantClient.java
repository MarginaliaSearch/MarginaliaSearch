package nu.marginalia.wmsa.edge.assistant.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Observable;
import nu.marginalia.wmsa.client.AbstractDynamicClient;
import nu.marginalia.wmsa.client.exception.RouteNotConfiguredException;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.assistant.dict.DictionaryResponse;
import org.eclipse.jetty.util.UrlEncoded;

import java.util.List;

@Singleton
public class AssistantClient extends AbstractDynamicClient {

    @Inject
    public AssistantClient() {
        super(ServiceDescriptor.EDGE_ASSISTANT);
    }

    public Observable<DictionaryResponse> dictionaryLookup(Context ctx, String word) {
        try {
            return super.get(ctx, "/dictionary/" + UrlEncoded.encodeString(word), DictionaryResponse.class);
        }
        catch (RouteNotConfiguredException ex) {
            return Observable.empty();
        }
    }

    @SuppressWarnings("unchecked")
    public Observable<List<String>> spellCheck(Context ctx, String word) {
        try {
            return (Observable<List<String>>) (Object) super.get(ctx, "/spell-check/" + UrlEncoded.encodeString(word), List.class);
        }
        catch (RouteNotConfiguredException ex) {
            return Observable.empty();
        }
    }
    public Observable<String> unitConversion(Context ctx, String value, String from, String to) {
        try {
            return super.get(ctx, "/unit-conversion?value=" + value + "&from=" + from + "&to=" + to);
        }
        catch (RouteNotConfiguredException ex) {
            return Observable.empty();
        }
    }

    public Observable<String> evalMath(Context ctx, String expression) {
        try {
            return super.get(ctx, "/eval-expression?value=" + UrlEncoded.encodeString(expression));
        }
        catch (RouteNotConfiguredException ex) {
            return Observable.empty();
        }
    }
}
