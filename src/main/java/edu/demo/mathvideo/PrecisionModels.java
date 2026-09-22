package edu.demo.mathvideo;

import java.util.List;

public final class PrecisionModels {
    private PrecisionModels() {}
    public record Skill(String id, String name, String topic, int grade, List<String> prerequisites) {}
    public record Cue(double start, double end, String text) {}
    public record Asset(String id, String title, String sourceUrl, String author, String fileName,
                        double duration, int grade, List<Cue> cues, String createdAt) {}
    public record Mark(String title, String skillId, double start, double end, String explanation, boolean reviewed) {}
    public record Clip(String id, String assetId, String title, String skillId, double start, double end,
                       int grade, String explanation, String fileName, double duration, String createdAt, String reviewMode) {}
    public record Diagnosis(String id, String question, String studentWork, String evidence,
                            boolean readable, boolean juniorMath, int grade, List<String> skillIds,
                            String clarification, String createdAt) {}
    public record Lesson(String clipId, String title, String skillName, String reason, double duration,
                         String playUrl, String sourceTitle, String sourceUrl, String author,
                         double sourceStart, double sourceEnd, String verificationLabel) {}
    public record Feedback(String diagnosisId, String clipId, String result, String createdAt) {}
}
