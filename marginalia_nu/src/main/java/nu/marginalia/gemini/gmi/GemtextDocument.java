package nu.marginalia.gemini.gmi;

import lombok.Getter;
import nu.marginalia.gemini.gmi.line.*;
import nu.marginalia.gemini.gmi.renderer.GemtextRenderer;
import nu.marginalia.gemini.gmi.renderer.GemtextRendererFactory;
import nu.marginalia.wmsa.memex.model.MemexNodeHeadingId;
import nu.marginalia.wmsa.memex.model.MemexNodeTaskId;
import nu.marginalia.wmsa.memex.model.MemexNodeUrl;
import nu.marginalia.wmsa.memex.model.MemexTaskState;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Getter
public class GemtextDocument extends Gemtext {
    private final Map<MemexNodeHeadingId, String> headings;
    private final Map<String, List<MemexNodeHeadingId>> headingsByName;
    private final Set<String> pragmas;
    private final List<GemtextTask> tasks;

    private final String title;
    private final String date;
    private final List<GemtextLink> links;
    private final int hashCode;

    private static final Pattern datePattern = Pattern.compile(".*(\\d{4}-\\d{2}-\\d{2}).*");
    private static final GemtextRenderer rawRenderer = new GemtextRendererFactory().gemtextRendererAsIs();

    public GemtextDocument(MemexNodeUrl url, String[] lines, MemexNodeHeadingId headingRoot) {
        super(url, lines, headingRoot);

        this.hashCode = Arrays.hashCode(lines);

        GemtextDataExtractor extractor = new GemtextDataExtractor();

        Arrays.stream(this.getLines()).forEach(extractor::take);

        this.headings = extractor.getHeadings();
        this.links = extractor.getLinks();
        this.title = Objects.requireNonNullElse(extractor.getTitle(), url.getUrl());
        this.pragmas = extractor.getPragmas();
        this.headingsByName = extractor.getHeadingsByName();
        this.tasks = extractor.getTasks();
        this.date = extractor.getDate();
    }

    public String getHeadingForElement(AbstractGemtextLine line) {
        return headings.getOrDefault(line.getHeading(), "");
    }

    public List<AbstractGemtextLine> getSection(MemexNodeHeadingId headingId) {
        return stream()
                .filter(line -> line.getHeading().isChildOf(headingId))
                .collect(Collectors.toList());
    }

    public String getSectionGemtext(MemexNodeHeadingId headingId) {
        if (headingId.equals(new MemexNodeHeadingId(0))) {
            return  stream()
                    .map(rawRenderer::renderLine)
                    .collect(Collectors.joining("\n"));
        }

        return stream()
                .filter(line -> line.getHeading().isChildOf(headingId))
                .map(rawRenderer::renderLine)
                .collect(Collectors.joining("\n"));
    }

    public Map<MemexNodeTaskId, Pair<String, MemexTaskState>> getOpenTopTasks() {
        return tasks.stream()
                .filter(task -> MemexTaskState.TODO.equals(task.getState())
                             || MemexTaskState.URGENT.equals(task.getState()))
                .filter(task -> task.getId().level() == 1)
                .collect(Collectors.toMap(GemtextTask::getId, task -> Pair.of(task.getTask(), task.getState())));
    }

    public static GemtextDocument of(MemexNodeUrl url, String... lines) {
        return new GemtextDocument(url, lines, new MemexNodeHeadingId(0));
    }

    public static GemtextDocument of(MemexNodeUrl url, Path file) throws IOException {
        try (var s = Files.lines(file)) {
            return new GemtextDocument(url, s.toArray(String[]::new), new MemexNodeHeadingId(0));
        }
    }

    public boolean isIndex() {
        return getUrl().getFilename().equals("index.gmi");
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public Optional<String> getHeading(MemexNodeHeadingId heading) {
        return Optional.ofNullable(headings.get(heading));
    }

    public Optional<MemexNodeHeadingId> getHeadingByName(MemexNodeHeadingId parent, String name) {
        var headings = headingsByName.get(name);
        if (null == headings) {
            return Optional.empty();
        }
        return headings.stream().filter(heading -> heading.isChildOf(parent)).findAny();
    }

    @Getter
    private static class GemtextDataExtractor extends GemtextLineVisitorAdapter<Object> {

        private String title;
        private String date;
        private final Map<MemexNodeHeadingId, String> headings = new TreeMap<>((a, b) -> Arrays.compare(a.getIds(), b.getIds()));
        private final Map<String, List<MemexNodeHeadingId>> headingsByName = new HashMap<>();
        private final Set<String> pragmas = new HashSet<>();
        private final List<GemtextLink> links = new ArrayList<>();
        private final List<GemtextTask> tasks = new ArrayList<>();

        @Override
        public Object visit(GemtextHeading g) {
            headings.put(g.getLevel(), g.getName());
            headingsByName.computeIfAbsent(g.getName(), t -> new ArrayList<>()).add(g.getLevel());

            if (title == null) {
                title = g.getName();
                var dateMatcher = datePattern.matcher(title);
                if (dateMatcher.matches()) {
                    date = dateMatcher.group(1);
                }
            }

            return null;
        }

        @Override
        public Object visit(GemtextLink g) {
            links.add(g);

            return null;
        }

        @Override
        public Object visit(GemtextTask g) {
            tasks.add(g);

            return null;
        }

        @Override
        public Object visit(GemtextPragma g) {
            pragmas.add(g.getLine());

            return null;
        }
    }
}
