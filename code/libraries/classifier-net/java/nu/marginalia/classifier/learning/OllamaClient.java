package nu.marginalia.classifier.learning;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class OllamaClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(OllamaClient.class);
    private static final Gson gson = new Gson();

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String modelName;

    public OllamaClient(String host, int port, String modelName) {
        this.httpClient = HttpClient.newHttpClient();
        this.endpoint = URI.create("http://" + host + ":" + port + "/api/generate");
        this.modelName = modelName;
    }

    public OllamaClient(String modelName) {
        this("localhost", 11434, modelName);
    }

    public boolean isAvailable() {
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

    public String generate(String prompt) throws IOException, InterruptedException {

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

        return response.body();
    }

    @Override
    public void close() {
        httpClient.close();
    }
}
