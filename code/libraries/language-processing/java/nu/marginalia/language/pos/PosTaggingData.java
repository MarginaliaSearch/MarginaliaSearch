package nu.marginalia.language.pos;


import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PosTaggingData {
    public final Path dictFilePath;
    public final Path rdrFilePath;
    public final Map<String, Integer> tags;

    public PosTaggingData(Path dictFilePath, Path rdrFilePath) {
        this.dictFilePath = dictFilePath;
        this.rdrFilePath = rdrFilePath;

        HashMap<String, Integer> tags = new HashMap<>();
        try (var linesStream = Files.lines(dictFilePath)) {
            linesStream.map(line -> StringUtils.split(line, " ", 2))
                    .filter(line -> line.length==2)
                    .map(line -> line[1])
                    .forEach(tag -> {
                        tags.putIfAbsent(tag, tags.size());
                    });

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.tags = Collections.unmodifiableMap(tags);
    }

    @Override
    public String toString() {
        return "PosTaggingData{" +
                "dictFilePath=" + dictFilePath +
                ", rdrFilePath=" + rdrFilePath +
                ", tags=" + tags +
                '}';
    }
}
