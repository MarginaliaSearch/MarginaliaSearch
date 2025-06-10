package nu.marginalia.ping.util;

import com.google.gson.Gson;
import nu.marginalia.model.gson.GsonFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public record JsonObject<T>(String json, Class<T> clazz) {
    private static final Gson gson = GsonFactory.get();

    @SuppressWarnings("unchecked")
    public JsonObject(T object) {
        this(gson.toJson(object), (Class<T>) object.getClass());
    }
    public JsonObject(byte[] compressedJson, Class<T> clazz) {
        this(decompress(compressedJson), clazz);
    }

    public T deserialize() {
        return gson.fromJson(json, clazz);
    }

    public byte[] compressed() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (var os = new GZIPOutputStream(baos)) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to compress JSON object", e);
        }
        return baos.toByteArray();
     }

     private static String decompress(byte[] compressedJson) {
        try (var gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressedJson))) {
            return new String(gzipInputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decompress JSON object", e);
        }
     }
}
