package nu.marginalia.nsfw.document;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import nu.marginalia.classifier.BinaryClassifierModel;
import nu.marginalia.classifier.BinaryClassifierTrainer;
import nu.marginalia.classifier.ClassifierVocabulary;
import nu.marginalia.classifier.learning.OllamaClient;
import nu.marginalia.integration.MarginaliaApiClient;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.gson.GsonFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public class NsfwDocumentModelSampleGathering {

    private final Gson gson = GsonFactory.get();

    private static String prompt(BufferedReader reader, String message) throws IOException {
        System.out.print(message);
        System.out.flush();
        return reader.readLine();
    }

    public static void main() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        String operation = prompt(reader, "Select an operation [db, query]: ");

        if (null == operation)
            return;

        if ("db".equals(operation)) {
            String dbFile = prompt(reader, "Enter the path to a link db file: ");

            if (null == dbFile)
                return;

            Path dbFileP = Path.of(dbFile);
            if (!Files.exists(dbFileP)) {
                System.out.println("No such file!");
            }
            else {
                runLinkDbMismatchGathering(dbFileP);
            }
        }
        else if ("query".equals(operation)) {
            String key = prompt(reader, "Input API key (or nothing to use public key, prone to rate limiting): ");
            if (key == null)
                return;
            if (key.isBlank()) key = "public";

            runMarginaliaSearchResultTraining(key);
        }
    }

    private static Path findDir(String frag) {
        Path candidate = Path.of(frag);
        if (Files.isDirectory(candidate)) {
            return candidate;
        }

        candidate = Path.of("../../..").resolve(frag);
        if (Files.isDirectory(candidate)) {
            return candidate;
        }

        throw new IllegalArgumentException("Could not find dir " + frag);
    }


    public static void runMarginaliaSearchResultTraining(String apiKey) throws IOException {
        Set<String> existingSamples = new HashSet<>();

        Path trainingDataDir = findDir("run/training-data/nsfw");

        for (Path p: Files.newDirectoryStream(trainingDataDir.resolve("samples"))) {
            for (String line: BinaryClassifierTrainer.lines(p)) {
                String[] parts = StringUtils.split(line, " ", 2);
                if (parts.length != 2)
                    continue;
                existingSamples.add(parts[1]);
            }
        }

        List<String> queries = new ArrayList<>();
        for (String line: Files.readAllLines(trainingDataDir.resolve("training-queries.txt"))) {
            if (line.startsWith("#"))
                continue;
            if (line.isBlank())
                continue;
            queries.add(line.trim());
        }

        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));
        Path outputPath = trainingDataDir.resolve("samples").resolve("ollama-" + timestamp + ".txt");

        try (var labeler = new OllamaNsfwLabeler();
             var marginaliaClient = new MarginaliaApiClient(apiKey);
             var trainingDataPw = new PrintWriter(Files.newOutputStream(outputPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
            )
        {
            if (!labeler.isAvailable()) {
                System.out.println("Local ollama instance is not available, or does not have the needed model");
                return;
            }

            Path modelPath = findDir("run/model/nsfw-model");

            BinaryClassifierModel model = BinaryClassifierModel.fromSerialized(modelPath);
            ClassifierVocabulary vocabulary = new ClassifierVocabulary(modelPath.resolve("vocabulary.txt"));;

            int page = 1;

            int count = 0;
            int agreedCount = 0;

            for (String query: queries) {
                int currentPage = page++;

                MarginaliaApiClient.ApiResponse apiResponse = marginaliaClient.query(ctx -> {
                    ctx.query(query + " qs=RF_TITLE"); // qs=RF_TITLE makes sure the search terms appear in the title
                    ctx.page(currentPage);
                    ctx.nsfw(0);
                    ctx.count(100);
                });

                if (apiResponse.results().isEmpty())
                    break;

                for (var result: apiResponse.results()) {
                    String title = result.title();
                    String description = result.description();

                    String input = (title + " " + description).replace('\n', ' ').toLowerCase();

                    if (!existingSamples.add(input)) {
                        continue;
                    }

                    var sample = vocabulary.createSample(BinaryClassifierModel.InputActivationMode.COUNTED, input, false);

                    boolean modelPrediction = model.predict(sample) > 0.5;
                    boolean qwenPrediction = labeler.classifyNsfw(title, description);

                    count++;

                    if (modelPrediction == qwenPrediction)
                        agreedCount++;

                    System.out.println("title: " + title);
                    System.out.println("desc: " + description);
                    System.out.println("model: " + modelPrediction);
                    System.out.println("qwen: " + qwenPrediction);
                    System.out.println("features: " + vocabulary.featuresReverse(sample.x()));
                    System.out.println("---");

                    if (qwenPrediction) {
                        trainingDataPw.println("__label__NSFW " + input);
                    }
                    else {
                        trainingDataPw.println("__label__SAFE " + input);
                    }

                    trainingDataPw.flush();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    public static void runLinkDbMismatchGathering(Path docDbPath) throws IOException {
        Set<String> existingSamples = new HashSet<>();

        Path trainingDataDir = findDir("run/training-data/nsfw");

        for (Path p: Files.newDirectoryStream(trainingDataDir.resolve("samples"))) {
            for (String line: BinaryClassifierTrainer.lines(p)) {
                String[] parts = StringUtils.split(line, " ", 2);
                if (parts.length != 2)
                    continue;
                existingSamples.add(parts[1]);
            }
        }

        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));
        Path outputPath = trainingDataDir.resolve("samples").resolve("ollama-" + timestamp + ".txt");

        try (Connection docDbConn = DriverManager.getConnection("jdbc:sqlite:" + docDbPath);
             var stmt = docDbConn.createStatement();
             var labeler = new OllamaNsfwLabeler();
             var trainingDataPw = new PrintWriter(Files.newOutputStream(outputPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
        )
        {
            if (!labeler.isAvailable()) {
                System.out.println("Local ollama instance is not available, or does not have the needed model");
                return;
            }


            Path modelPath = findDir("run/model/nsfw-model");

            BinaryClassifierModel model = BinaryClassifierModel.fromSerialized(modelPath);
            ClassifierVocabulary vocabulary = new ClassifierVocabulary(modelPath.resolve("vocabulary.txt"));;


            int count = 0;
            int agreedCount = 0;

            Object2IntOpenHashMap<EdgeDomain> countsByDomain = new Object2IntOpenHashMap<>();
            countsByDomain.defaultReturnValue(1);

            ResultSet rs = stmt.executeQuery("SELECT TITLE, DESCRIPTION, URL FROM DOCUMENT");
            stmt.setFetchSize(1000);

            while (rs.next()) {
                String urlStr = rs.getString("URL");

                String title = rs.getString("TITLE");
                String description = rs.getString("DESCRIPTION");

                String input = (title + " " + description).replace('\n', ' ').toLowerCase();

                if (!existingSamples.add(input)) {
                    continue;
                }

                var countedFeatures = vocabulary.createSample(BinaryClassifierModel.InputActivationMode.COUNTED, input, false);

                boolean modelPrediction = model.predict(countedFeatures) > 0.5;
                if (!modelPrediction)
                    continue;

                // Allow at most 5 entries per domain
                var cntOpt = EdgeUrl.parse(urlStr)
                        .map(EdgeUrl::getDomain)
                        // addTo returns the previous value, but default return value is 1, so... the current count
                        .map(domain -> countsByDomain.addTo(domain, 1));


                if (cntOpt.isEmpty() || cntOpt.get() >= 5)
                    continue;

                boolean qwenPrediction = labeler.classifyNsfw(title, description);

                count++;

                if (modelPrediction == qwenPrediction)
                    agreedCount++;

                System.out.println("title: " + title);
                System.out.println("desc: " + description);
                System.out.println("model: " + modelPrediction);
                System.out.println("qwen: " + qwenPrediction);
                System.out.println("features: " + vocabulary.featuresReverse(countedFeatures.x()));
                System.out.printf("aggregate agreement: %2.2f\n", agreedCount / (double) count);
                System.out.println("---");

                if (qwenPrediction) {
                    trainingDataPw.println("__label__NSFW " + input);
                }
                else {
                    trainingDataPw.println("__label__SAFE " + input);
                }

                trainingDataPw.flush();
            }
        } catch (IOException | SQLException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}



class OllamaNsfwLabeler implements AutoCloseable {

    public static final String DEFAULT_MODEL = "qwen3:8b";

    private final OllamaClient client;
    private final Gson gson = GsonFactory.get();

    public OllamaNsfwLabeler() {
        this.client = new OllamaClient(Objects.requireNonNullElse(System.getenv("LABELING_MODEL"), DEFAULT_MODEL));
    }

    public OllamaNsfwLabeler(OllamaClient client) {
        this.client = client;
    }

    private static final Logger logger = LoggerFactory.getLogger(OllamaNsfwLabeler.class);

    public boolean isAvailable() {
        return client.isAvailable();
    }

    /** Ask the LLM to classify the given title and description as
     *  NSFW or SAFE.  Returns NsfwDocumentModel.LABEL_NSFW or NsfwDocumentModel.LABEL_SAFE.
     *
     * @throws IOException if classification fails
     */
    boolean classifyNsfw(String title, String description) throws IOException, InterruptedException {
        String prompt = buildPrompt(title, description);

        String jsonResponse = client.generate(prompt);

        if (jsonResponse == null || jsonResponse.isEmpty()) {
            throw new IOException("Empty response from Ollama");
        }

        JsonObject json = gson.fromJson(jsonResponse, JsonObject.class);

        if (!json.has("response")) {
            throw new IOException("Ollama response missing 'response' field");
        }

        String responseText = json.get("response").getAsString().trim().toUpperCase();

        if (responseText.contains("NSFW")) {
            return true;
        } else if (responseText.contains("SAFE")) {
            return false;
        }

        throw new IOException("Could not parse Ollama response as NSFW/SAFE: '" + responseText + "'");
    }

    private static String buildPrompt(String title, String description) {
        return """
                You are a content classifier for a search engine.

                Your job is to label documents as SAFE or NSFW.

                NSFW includes: explicit sexual content, graphic violence, hate speech,
                escort services, swinger parties, hookup sites, sex toys, cam girl websites,
                prostitution, scams, counterfeit drugs, spam websites.

                Medical, educational, news articles, and artistic content should generally be SAFE.

                When in doubt, consider whether the content would be appropriate for a
                younger audience.

                Do not explain your reasoning.
                """
                + "Title: " + title + "\n"
                + "Description: " + description;
    }

    @Override
    public void close() {
        client.close();
    }
}
