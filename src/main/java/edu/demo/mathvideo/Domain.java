package edu.demo.mathvideo;

import java.util.List;

public final class Domain {
    private Domain() {}

    public record Video(String id, String title, String fileName, Double durationSeconds, String createdAt) {}
    public record KnowledgePoint(String id, String name, List<String> aliases, List<String> prerequisites) {}
    public record Segment(String id, String videoId, double startSeconds, double endSeconds,
                          String transcript, String visualText, String summary,
                          List<String> knowledgePointIds, int difficulty, String teachingType,
                          String keyframeFile) {}
    public record TranscriptItem(double startSeconds, double endSeconds, String text) {}
    public record TranscriptRequest(List<TranscriptItem> segments) {}
    public record SearchHit(String segmentId, String videoId, String videoTitle,
                            double startSeconds, double endSeconds, String transcript,
                            String visualText, List<String> knowledgePoints, double score,
                            String reason, String keyframeUrl, String videoUrl) {}
    public record Answer(String answer, List<SearchHit> sources, boolean generatedByModel) {}
    public record PlanRequest(String question, List<String> masteredKnowledgePointIds) {}
    public record PlanStep(int order, String knowledgePointId, String knowledgePoint,
                           String reason, SearchHit video) {}
    public record LearningPlan(String question, List<PlanStep> steps, String note) {}
}
