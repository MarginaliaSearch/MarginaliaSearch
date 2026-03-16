package nu.marginalia.nsfw.document;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import nu.marginalia.classifier.BinaryClassifierModel;
import nu.marginalia.classifier.BinaryClassifierTrainer;
import nu.marginalia.classifier.ClassifierVocabulary;
import nu.marginalia.classifier.learning.OllamaClient;
import nu.marginalia.integration.MarginaliaApiClient;
import nu.marginalia.model.gson.GsonFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

public class NsfwDocumentModelSampleGathering {

    private final Gson gson = GsonFactory.get();

    public static void main() throws IOException {
        runMarginaliaSearchResultTraining();
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


    public static void runMarginaliaSearchResultTraining() throws IOException {
        List<String> queries = List.of("lesbian", "porn", "sexy", "big tits");
        Set<String> existingSamples = new HashSet<>();

        Path trainingDataDir = findDir("run/training-data/nsfw/samples");

        for (Path p: Files.newDirectoryStream(trainingDataDir)) {
            for (String line: BinaryClassifierTrainer.lines(p)) {
                String[] parts = StringUtils.split(line, " ", 2);
                if (parts.length != 2)
                    continue;
                existingSamples.add(parts[1]);
            }
        }

        String apiKey = Objects.requireNonNullElse(System.getenv("MARGINALIA_API_KEY"), "public");

        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));
        Path outputPath = trainingDataDir.resolve("ollama-" + timestamp + ".txt.gz");

        try (var labeler = new OllamaNsfwLabeler();
             var marginaliaClient = new MarginaliaApiClient(apiKey);
             var trainingDataPw = new PrintWriter(new GZIPOutputStream(Files.newOutputStream(outputPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)))
            )
        {
            if (!labeler.isAvailable()) {
                System.out.println("Local ollama instance is not available");
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

                    int[] features = vocabulary.features(input);

                    boolean modelPrediction = model.predict(features) > 0.5;
                    boolean qwenPrediction = labeler.classifyNsfw(title, description);

                    count++;

                    if (modelPrediction == qwenPrediction)
                        agreedCount++;

                    System.out.println("title: " + title);
                    System.out.println("desc: " + description);
                    System.out.println("model: " + modelPrediction);
                    System.out.println("qwen: " + qwenPrediction);
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


}



class OllamaNsfwLabeler implements AutoCloseable {

    public static final String DEFAULT_MODEL = "qwen3:8b";

    private final OllamaClient client;
    private final Gson gson = GsonFactory.get();

    public OllamaNsfwLabeler() {
        this.client = new OllamaClient(DEFAULT_MODEL);
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
