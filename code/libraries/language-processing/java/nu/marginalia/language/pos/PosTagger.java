package nu.marginalia.language.pos;


import com.github.datquocnguyen.RDRPOSTagger;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /** Alternate constructor for tests */
    public PosTagger(String isoCode, List<String> tags) {
        this.isoCode = isoCode;
        this.tagNames  = tags.stream().distinct().toList();
        this.tagDict = tags.stream().distinct().collect(Collectors.toMap(Function.identity(), tagNames::indexOf, (a,b)->a));
        this.rdrposTagger = null;
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

        // Encode the tags as a bit mask.  These will just have one (or zero) bits set
        // but will match against more complex masks

        long[] encodedTags = new long[tags.length];
        for (int i = 0; i < encodedTags.length; i++) {
            encodedTags[i] = encodeTagName(tags[i]);
        }

        return encodedTags;
    }

    public long encodeTagName(String tagName) {
        Integer tag = tagDict.get(tagName);
        if (tag == null) {
            return 0L;
        }
        return 1L << tag;
    }

    public long encodeTagNames(List<String> tagNames) {
        long ret = 0;
        for (String tagName : tagNames) {
            ret |= encodeTagName(tagName);
        }
        return ret;
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

    public OptionalInt tagId(String tagName) {
        Integer id = tagDict.get(tagName);
        if (id == null)
            return OptionalInt.empty();
        return OptionalInt.of(id);
    }

    public List<String> tags() {
        var ret = new ArrayList<>(tagDict.keySet());
        ret.sort(Comparator.naturalOrder());
        return ret;
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
