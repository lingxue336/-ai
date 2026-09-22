package edu.demo.mathvideo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmbeddingService {
    private final ObjectMapper mapper;
    private final String ollamaUrl;
    private final String model;
    private final Map<String, double[]> cache = new ConcurrentHashMap<>();
    private volatile long unavailableUntil;

    public EmbeddingService(ObjectMapper mapper,
                            @Value("${app.ollama-url:}") String ollamaUrl,
                            @Value("${app.ollama-embedding-model:}") String model) {
        this.mapper = mapper; this.ollamaUrl = ollamaUrl; this.model = model;
    }

    public boolean isConfigured() { return !ollamaUrl.isBlank() && !model.isBlank(); }

    public double[] embed(String text) {
        if (!isConfigured() || text == null || text.isBlank() || System.currentTimeMillis() < unavailableUntil) return null;
        return cache.computeIfAbsent(text, this::requestEmbedding);
    }

    private double[] requestEmbedding(String text) {
        try {
            byte[] body = mapper.writeValueAsBytes(Map.of("model", model, "input", text));
            HttpRequest request = HttpRequest.newBuilder(URI.create(ollamaUrl.replaceAll("/$", "") + "/api/embed"))
                .timeout(Duration.ofSeconds(8)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) { unavailableUntil = System.currentTimeMillis() + 30_000; return null; }
            JsonNode values = mapper.readTree(response.body()).path("embeddings").path(0);
            if (!values.isArray() || values.isEmpty()) return null;
            double[] vector = new double[values.size()];
            for (int i = 0; i < vector.length; i++) vector[i] = values.get(i).asDouble();
            return vector;
        } catch (Exception ex) { unavailableUntil = System.currentTimeMillis() + 30_000; return null; }
    }

    public double similarity(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i]; }
        return aa == 0 || bb == 0 ? 0 : dot / Math.sqrt(aa * bb);
    }
}
