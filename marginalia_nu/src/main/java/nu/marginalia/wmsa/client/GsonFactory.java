package nu.marginalia.wmsa.client;

import com.google.gson.*;
import marcono1234.gson.recordadapter.RecordTypeAdapterFactory;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.id.EdgeId;

import java.net.URISyntaxException;

public class GsonFactory {
    public static Gson get() {
        return new GsonBuilder()
                .registerTypeAdapterFactory(RecordTypeAdapterFactory.builder().allowMissingComponentValues().create())
                .registerTypeAdapter(EdgeUrl.class, (JsonSerializer<EdgeUrl>) (src, typeOfSrc, context) -> new JsonPrimitive(src.toString()))
                .registerTypeAdapter(EdgeDomain.class, (JsonSerializer<EdgeDomain>) (src, typeOfSrc, context) -> new JsonPrimitive(src.toString()))
                .registerTypeAdapter(EdgeUrl.class, (JsonDeserializer<EdgeUrl>) (json, typeOfT, context) -> {
                    try {
                        return new EdgeUrl(json.getAsString());
                    } catch (URISyntaxException e) {
                        throw new JsonParseException("URL Parse Exception", e);
                    }
                })
                .registerTypeAdapter(EdgeDomain.class, (JsonDeserializer<EdgeDomain>) (json, typeOfT, context) -> new EdgeDomain(json.getAsString()))
                .registerTypeAdapter(EdgeId.class, (JsonDeserializer<EdgeId<?>>) (json, typeOfT, context) -> new EdgeId<>(json.getAsInt()))
                .registerTypeAdapter(EdgeId.class, (JsonSerializer<EdgeId<?>>) (src, typeOfSrc, context) -> new JsonPrimitive(src.id()))
                .create();
    }
}
