package nu.marginalia.assistant.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Observable;
import nu.marginalia.assistant.client.model.DictionaryResponse;
import nu.marginalia.client.AbstractDynamicClient;
import nu.marginalia.client.exception.RouteNotConfiguredException;
import nu.marginalia.WmsaHome;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.client.Context;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Singleton
public class AssistantClient extends AbstractDynamicClient {

    @Inject
    public AssistantClient(ServiceDescriptors descriptors) {
        super(descriptors.forId(ServiceId.Assistant), WmsaHome.getHostsFile(), GsonFactory::get);
    }

    public Observable<DictionaryResponse> dictionaryLookup(Context ctx, String word) {
        try {
            return super.get(ctx, "/dictionary/" + URLEncoder.encode(word, StandardCharsets.UTF_8), DictionaryResponse.class);
        }
        catch (RouteNotConfiguredException ex) {
            return Observable.empty();
        }
    }

    @SuppressWarnings("unchecked")
    public Observable<List<String>> spellCheck(Context ctx, String word) {
        try {
            return (Observable<List<String>>) (Object) super.get(ctx, "/spell-check/" +  URLEncoder.encode(word, StandardCharsets.UTF_8), List.class);
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
            return super.get(ctx, "/eval-expression?value=" +  URLEncoder.encode(expression, StandardCharsets.UTF_8));
        }
        catch (RouteNotConfiguredException ex) {
            return Observable.empty();
        }
    }
}
