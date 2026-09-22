package edu.demo.mathvideo;

import edu.demo.mathvideo.Domain.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SearchEngine {
    private final LibraryStore store;
    private final EmbeddingService embeddings;

    public SearchEngine(LibraryStore store, EmbeddingService embeddings) {
        this.store = store; this.embeddings = embeddings;
    }

    public List<String> matchKnowledge(String text) {
        String q = normalize(text);
        List<String> ids = new ArrayList<>();
        for (KnowledgePoint point : store.knowledgePoints()) {
            if (point.aliases().stream().anyMatch(a -> q.contains(normalize(a)))) ids.add(point.id());
        }
        return ids;
    }

    public List<SearchHit> search(String question, int requestedTopK) {
        if (question == null || question.isBlank()) return List.of();
        int topK = Math.max(1, Math.min(requestedTopK, 20));
        Set<String> matched = new HashSet<>(matchKnowledge(question));
        double[] queryVector = embeddings.embed(question);
        Map<String, KnowledgePoint> points = pointMap();
        Map<String, Video> videos = new HashMap<>();
        for (Video video : store.videos()) videos.put(video.id(), video);
        List<SearchHit> results = new ArrayList<>();

        for (Segment segment : store.segments()) {
            double textScore = similarity(question, segment.transcript());
            double summaryScore = similarity(question, segment.summary());
            double visualScore = similarity(question, segment.visualText());
            double vectorScore = embeddings.similarity(queryVector,
                embeddings.embed(segment.transcript() + " " + segment.visualText() + " " + segment.summary()));
            double pointScore = 0;
            List<String> names = new ArrayList<>();
            for (String id : segment.knowledgePointIds()) {
                KnowledgePoint point = points.get(id);
                if (point == null) continue;
                names.add(point.name());
                if (matched.contains(id)) pointScore += 3.0;
                else if (point.aliases().stream().anyMatch(a -> similarity(question, a) > 0.55)) pointScore += 0.8;
            }
            double score = textScore * 2.1 + summaryScore * 1.6 + visualScore * 1.0
                + Math.max(0, vectorScore) * 2.0 + pointScore;
            if (score <= 0.03) continue;
            Video video = videos.get(segment.videoId());
            String reason = pointScore > 0 ? "命中知识点：" + String.join("、", names)
                : vectorScore > 0.65 ? "语义向量相关" : visualScore > textScore ? "关键帧文字相关" : "字幕内容相关";
            results.add(new SearchHit(segment.id(), segment.videoId(), video == null ? "未知视频" : video.title(),
                segment.startSeconds(), segment.endSeconds(), segment.transcript(), segment.visualText(), names,
                Math.round(score * 1000.0) / 1000.0, reason,
                segment.keyframeFile() == null ? null : "/api/frames/" + segment.keyframeFile(),
                video == null || video.fileName() == null ? null : "/api/videos/" + video.id() + "/file"));
        }
        results.sort(Comparator.comparingDouble(SearchHit::score).reversed()
            .thenComparing(SearchHit::startSeconds));
        return results.stream().limit(topK).toList();
    }

    public LearningPlan plan(PlanRequest request) {
        String question = request == null || request.question() == null ? "" : request.question().trim();
        if (question.isBlank()) return new LearningPlan(question, List.of(), "请输入想学习的数学问题。");
        Map<String, KnowledgePoint> map = pointMap();
        LinkedHashSet<String> targets = new LinkedHashSet<>(matchKnowledge(question));
        if (targets.isEmpty()) {
            List<SearchHit> hits = search(question, 1);
            if (!hits.isEmpty()) {
                for (KnowledgePoint point : map.values()) {
                    if (hits.get(0).knowledgePoints().contains(point.name())) targets.add(point.id());
                }
            }
        }
        if (targets.isEmpty()) return new LearningPlan(question, List.of(), "尚未识别出对应知识点；请换成更具体的数学概念。 ");
        Set<String> mastered = request.masteredKnowledgePointIds() == null ? Set.of() : Set.copyOf(request.masteredKnowledgePointIds());
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String target : targets) visit(target, map, ordered, new HashSet<>());
        List<PlanStep> steps = new ArrayList<>();
        for (String id : ordered) {
            if (mastered.contains(id)) continue;
            KnowledgePoint point = map.get(id);
            if (point == null) continue;
            List<SearchHit> hits = search(point.name(), 1);
            SearchHit matchingVideo = hits.stream().filter(h -> h.knowledgePoints().contains(point.name())).findFirst().orElse(null);
            steps.add(new PlanStep(steps.size() + 1, id, point.name(),
                targets.contains(id) ? "目标知识点" : "建议先掌握的前置知识",
                matchingVideo));
        }
        return new LearningPlan(question, steps, "按前置知识到目标知识排序；已掌握知识点已跳过。视频推荐来自现有片段。 ");
    }

    private void visit(String id, Map<String, KnowledgePoint> map, LinkedHashSet<String> ordered, Set<String> visiting) {
        if (ordered.contains(id) || !visiting.add(id)) return;
        KnowledgePoint point = map.get(id);
        if (point != null) for (String pre : point.prerequisites()) visit(pre, map, ordered, visiting);
        ordered.add(id);
        visiting.remove(id);
    }

    private Map<String, KnowledgePoint> pointMap() {
        Map<String, KnowledgePoint> map = new HashMap<>();
        for (KnowledgePoint point : store.knowledgePoints()) map.put(point.id(), point);
        return map;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}，。！？、；：‘’“”（）\u00b1]", "");
    }

    private double similarity(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isBlank() || b.isBlank()) return 0;
        if (b.contains(a)) return 1;
        Set<String> aa = grams(a), bb = grams(b);
        int intersection = 0;
        for (String gram : aa) if (bb.contains(gram)) intersection++;
        return 2.0 * intersection / (aa.size() + bb.size());
    }

    private Set<String> grams(String text) {
        Set<String> out = new HashSet<>();
        if (text.length() == 1) out.add(text);
        for (int i = 0; i + 1 < text.length(); i++) out.add(text.substring(i, i + 2));
        return out;
    }
}
