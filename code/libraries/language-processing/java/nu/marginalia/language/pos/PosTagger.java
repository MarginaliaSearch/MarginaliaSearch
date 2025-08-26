package nu.marginalia.language.pos;


import com.github.datquocnguyen.RDRPOSTagger;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PosTagger {
    private final RDRPOSTagger rdrposTagger;
    public final Map<String, Integer> tagDict;
    public final List<String> tagNames;
    private final String isoCode;

    public PosTagger(String isoCode, Path dictFilePath, Path rdrFilePath) throws IOException {
        this.isoCode = isoCode;
        rdrposTagger = new RDRPOSTagger(dictFilePath, rdrFilePath);

        List<String> tagNames = new ArrayList<>();
        HashMap<String, Integer> tags = new HashMap<>();
        try (var linesStream = Files.lines(dictFilePath)) {
            linesStream.map(line -> StringUtils.split(line, " ", 2))
                    .filter(line -> line.length==2)
                    .map(line -> line[1])
                    .distinct()
                    .forEach(tag -> {
                        tags.putIfAbsent(tag, tagNames.size());
                        tagNames.add(tag);
                    });

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.tagDict = Collections.unmodifiableMap(tags);
        this.tagNames = Collections.unmodifiableList(tagNames);
    }

    public long[] tagSentence(String[] words) {
        String[] tags;

        // Unclear if this is necessary, but the library does have a different function for tagging English

        if ("en".equalsIgnoreCase(isoCode)) {
            tags = rdrposTagger.tagsForEnSentence(words);
        }
        else {
            tags = rdrposTagger.tagSentence(words);
        }


        long[] encodedTags = new long[tags.length];
        for (int i = 0; i < encodedTags.length; i++) {
            encodedTags[i] = 1L << tagDict.getOrDefault(tags[i], 65);
        }

        return encodedTags;
    }

    public String decodeTagName(long encodedTag) {
        if (encodedTag == 0)
            return "?";
        return tagName(Long.numberOfTrailingZeros(encodedTag));
    }

    public String tagName(int tagId) {
        if (tagId < 0 || tagId >= tagNames.size())
            return "?";
        return tagNames.get(tagId);
    }

    public List<String> tags() {
        return tagDict.keySet().stream().sorted().toList();
    }
    public OptionalInt tagId(String tagName) {
        Integer id = tagDict.get(tagName);
        if (id == null)
            return OptionalInt.empty();
        return OptionalInt.of(id);
    }

    public IntList tagIdsForPrefix(String tagNamePrefix) {
        IntArrayList ret = new IntArrayList();
        tagDict.entrySet().stream()
                .filter(tag -> tag.getKey().startsWith(tagNamePrefix))
                .mapToInt(Map.Entry::getValue)
                .forEach(ret::add);
        return ret;
    }

    @Override
    public String toString() {
        return "PosTaggingData{ tags=" + tagDict + '}';
    }
}
