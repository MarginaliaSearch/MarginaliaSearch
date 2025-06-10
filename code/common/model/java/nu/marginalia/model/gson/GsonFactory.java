package nu.marginalia.model.gson;

import com.google.gson.*;
import marcono1234.gson.recordadapter.RecordTypeAdapterFactory;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;

import java.net.URISyntaxException;
import java.time.Instant;

public class GsonFactory {
    public static Gson get() {
        return new GsonBuilder()
                .registerTypeAdapterFactory(RecordTypeAdapterFactory.builder().allowMissingComponentValues().create())
                .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (src, typeOfSrc, context) -> new JsonPrimitive(src.toEpochMilli()))
                .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, typeOfT, context) -> {
                    if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
                        return Instant.ofEpochMilli(json.getAsLong());
                    } else {
                        throw new JsonParseException("Expected a number for Instant");
                    }
                })
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
                .serializeSpecialFloatingPointValues()
                .create();
    }
}
