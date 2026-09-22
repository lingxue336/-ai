package edu.demo.mathvideo;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.demo.mathvideo.Domain.Answer;
import edu.demo.mathvideo.Domain.SearchHit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class AnswerService {
    private final SearchEngine search;
    private final ObjectMapper mapper;
    private final String ollamaUrl;
    private final String model;

    public AnswerService(SearchEngine search, ObjectMapper mapper,
                         @Value("${app.ollama-url:}") String ollamaUrl,
                         @Value("${app.ollama-model:}") String model) {
        this.search = search; this.mapper = mapper; this.ollamaUrl = ollamaUrl; this.model = model;
    }

    public Answer answer(String question) {
        List<SearchHit> hits = search.search(question, 3);
        if (hits.isEmpty()) return new Answer("没有找到相关片段，请换一种更具体的问法。", List.of(), false);
        if (!ollamaUrl.isBlank() && !model.isBlank()) {
            try {
                StringBuilder context = new StringBuilder();
                for (int i = 0; i < hits.size(); i++) {
                    SearchHit hit = hits.get(i);
                    context.append("[来源").append(i + 1).append("] ").append(hit.videoTitle())
                        .append(" ").append(hit.startSeconds()).append("-").append(hit.endSeconds())
                        .append("秒：").append(hit.transcript()).append(" 画面文字：")
                        .append(hit.visualText()).append("\n");
                }
                String prompt = "你是初中数学学习助手。只能根据给定来源回答；若来源不足，明确说不知道。回答简洁，并标注[来源序号]。\n问题："
                    + question + "\n" + context;
                byte[] body = mapper.writeValueAsBytes(Map.of("model", model, "prompt", prompt, "stream", false));
                HttpRequest request = HttpRequest.newBuilder(URI.create(ollamaUrl.replaceAll("/$", "") + "/api/generate"))
                    .timeout(Duration.ofSeconds(90)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
                HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 == 2) {
                    String generated = mapper.readTree(response.body()).path("response").asText();
                    if (!generated.isBlank()) return new Answer(generated, hits, true);
                }
            } catch (Exception ignored) { /* Keep grounded fallback available when model is offline. */ }
        }
        SearchHit best = hits.get(0);
        return new Answer("最相关的是《" + best.videoTitle() + "》" + Math.round(best.startSeconds()) + "–"
            + Math.round(best.endSeconds()) + "秒。该片段讲到：" + best.transcript()
            + "（以下是检索结果，不是大模型生成的解答。）", hits, false);
    }

    public boolean isModelConfigured() { return !ollamaUrl.isBlank() && !model.isBlank(); }
}
