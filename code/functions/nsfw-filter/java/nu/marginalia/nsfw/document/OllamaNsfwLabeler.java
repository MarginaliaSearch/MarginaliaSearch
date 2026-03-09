package nu.marginalia.nsfw.document;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Labels ambiguous documents as NSFW or SAFE by querying a
 *  local Ollama LLM instance.  Used during active learning
 *  to generate new training data for the NSFW classifier.
 */
class OllamaNsfwLabeler implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(OllamaNsfwLabeler.class);
    private static final Gson gson = new Gson();

    static final String DEFAULT_MODEL = "qwen3:8b";

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String modelName;

    OllamaNsfwLabeler(String host, int port, String modelName) {
        this.httpClient = HttpClient.newHttpClient();
        this.endpoint = URI.create("http://" + host + ":" + port + "/api/generate");
        this.modelName = modelName;
    }

    /** Check whether Ollama is reachable and the configured model
     *  is available.
     */
    boolean isAvailable() {
        try {
            URI tagsUri = URI.create(
                    endpoint.getScheme() + "://" + endpoint.getHost()
                    + ":" + endpoint.getPort() + "/api/tags");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(tagsUri)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200
                    && response.body().contains(modelName);
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /** Ask the LLM to classify the given title and description as
     *  NSFW or SAFE.  Returns NsfwDocumentModel.LABEL_NSFW or NsfwDocumentModel.LABEL_SAFE.
     *
     * @throws IOException if classification fails
     */
    String classify(String title, String description) throws IOException, InterruptedException {
        String prompt = buildPrompt(title, description);

        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", modelName);
        requestJson.addProperty("prompt", prompt);
        requestJson.addProperty("stream", false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(endpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestJson)))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Ollama returned HTTP " + response.statusCode());
        }

        return parseLabel(response.body());
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

    /** Extract the label from the Ollama JSON response.
     *
     * @throws IOException if the response is missing, empty, or does not
     *                     contain a recognizable NSFW/SAFE label
     */
    static String parseLabel(String jsonResponse) throws IOException {
        if (jsonResponse == null || jsonResponse.isEmpty()) {
            throw new IOException("Empty response from Ollama");
        }

        JsonObject json = gson.fromJson(jsonResponse, JsonObject.class);

        if (!json.has("response")) {
            throw new IOException("Ollama response missing 'response' field");
        }

        String responseText = json.get("response").getAsString().trim().toUpperCase();

        if (responseText.contains("NSFW")) {
            return NsfwDocumentModel.LABEL_NSFW;
        } else if (responseText.contains("SAFE")) {
            return NsfwDocumentModel.LABEL_SAFE;
        }

        throw new IOException("Could not parse Ollama response as NSFW/SAFE: '" + responseText + "'");
    }

    @Override
    public void close() {
        httpClient.close();
    }
}
