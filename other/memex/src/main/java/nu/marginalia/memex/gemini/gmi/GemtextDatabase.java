package nu.marginalia.memex.gemini.gmi;

import com.google.common.collect.Sets;
import nu.marginalia.memex.gemini.gmi.line.GemtextLineVisitorAdapter;
import nu.marginalia.memex.gemini.gmi.line.GemtextLink;
import nu.marginalia.memex.memex.model.MemexNodeUrl;
import nu.marginalia.memex.memex.model.MemexUrl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class GemtextDatabase extends Gemtext {
    public final Map<String, Integer> links;

    public GemtextDatabase(MemexNodeUrl url, String[] lines) {
        super(url, lines);

        links = new HashMap<>();
        for (int i = 0; i < size(); i++) {
            int linkIdx = i;

            get(i).visit(new GemtextLineVisitorAdapter<>() {
                @Override
                public Object visit(GemtextLink g) {
                    links.put(g.getUrl().toString(), linkIdx);
                    return null;
                }
            });
        }
    }

    public Set<String> keys() {
        return links.keySet();
    }

    public Optional<String> getLinkData(MemexUrl url) {
        Integer idx = links.get(url.getUrl());
        if (idx != null) {
            return
                    Optional.of(get(idx).mapLink(GemtextLink::getTitle).orElse(""));
        }
        return Optional.empty();
    }


    public static GemtextDatabase of(MemexNodeUrl url, String[] lines) {
        return new GemtextDatabase(url, lines);
    }

    public static GemtextDatabase of(MemexNodeUrl url, Path file) throws IOException {
        try (var s = Files.lines(file)) {
            return new GemtextDatabase(url, s.toArray(String[]::new));
        }
    }

    public Set<MemexNodeUrl> difference(GemtextDatabase other) {
        Set<MemexNodeUrl> differences = new HashSet<>();

        Sets.difference(keys(), other.keys()).stream().map(MemexNodeUrl::new).forEach(differences::add);

        Sets.intersection(keys(), other.keys())
                .stream()
                .map(MemexNodeUrl::new)
                .filter(url -> !Objects.equals(getLinkData(url), other.getLinkData(url)))
                .forEach(differences::add);

        return differences;
    }
}
