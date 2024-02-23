package nu.marginalia.integration.reddit;

import com.github.luben.zstd.ZstdInputStream;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import nu.marginalia.integration.reddit.model.RawRedditComment;
import nu.marginalia.integration.reddit.model.RawRedditSubmission;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;

public class RedditEntryReader {
    public static EntryIterator<RawRedditSubmission> readSubmissions(Path file) throws IOException {
        return new EntryIterator<>(file, RawRedditSubmission.class);
    }

    public static EntryIterator<RawRedditComment> readComments(Path file) throws IOException {
        return new EntryIterator<>(file, RawRedditComment.class);
    }

    public static class EntryIterator<T> implements Iterator<T>, AutoCloseable {
        private final JsonReader reader;
        private final Class<T> type;
        private final Gson gson = new GsonBuilder().create();

        private EntryIterator(Path file, Class<T> type) throws IOException {
            this.type = type;

            reader = new JsonReader(new InputStreamReader(new ZstdInputStream(Files.newInputStream(file, StandardOpenOption.READ))));

            // Set the reader to be lenient to allow multiple top level objects
            reader.setLenient(true);
        }

        @Override
        public boolean hasNext() {
            try {
                return reader.hasNext();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        public T next() {
            return gson.fromJson(reader, type);
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}
