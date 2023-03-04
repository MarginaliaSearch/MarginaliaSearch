package nu.marginalia.model.gson;

import com.google.gson.*;
import marcono1234.gson.recordadapter.RecordTypeAdapterFactory;
import nu.marginalia.bigstring.BigString;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.id.EdgeId;

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
                .registerTypeAdapter(BigString.class, (JsonDeserializer<BigString>) (json, typeOfT, context) -> BigString.encode(json.getAsString()))
                .registerTypeAdapter(BigString.class, (JsonSerializer<BigString>) (src, typeOfT, context) -> new JsonPrimitive(src.decode()))
                .serializeSpecialFloatingPointValues()
                .create();
    }
}
