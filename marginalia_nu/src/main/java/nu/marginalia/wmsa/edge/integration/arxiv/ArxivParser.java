package nu.marginalia.wmsa.edge.integration.arxiv;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import nu.marginalia.wmsa.edge.integration.arxiv.model.ArxivMetadata;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ArxivParser {
    private final Gson gson = new GsonBuilder().create();

    public ArxivParser() {

    }

    public List<ArxivMetadata> parse(File jsonFile) throws IOException {

        List<ArxivMetadata> ret = new ArrayList<>();
        try (var lines = Files.lines(jsonFile.toPath())) {
            lines.map(line -> gson.fromJson(line, ArxivMetadata.class)).forEach(ret::add);
        }

        return ret;
    }
}
