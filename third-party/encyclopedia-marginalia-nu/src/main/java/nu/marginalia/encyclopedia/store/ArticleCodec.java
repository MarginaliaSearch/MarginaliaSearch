package nu.marginalia.encyclopedia.store;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdInputStream;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import marcono1234.gson.recordadapter.RecordTypeAdapterFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class ArticleCodec {
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapterFactory(RecordTypeAdapterFactory.builder().allowMissingComponentValues().create())
            .create();

    public static byte[] toCompressedJson(Object any) {
        return Zstd.compress(gson.toJson(any).getBytes());
    }
    public static <T> T fromCompressedJson(byte[] stream, Class<T> type) throws IOException {
        return gson.fromJson(new InputStreamReader(new ZstdInputStream(new ByteArrayInputStream(stream))), type);
    }

}
