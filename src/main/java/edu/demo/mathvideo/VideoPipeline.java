package edu.demo.mathvideo;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.demo.mathvideo.Domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class VideoPipeline {
    private final LibraryStore store;
    private final SearchEngine search;
    private final ObjectMapper mapper;
    private final String ffmpeg;
    private final String ffprobe;
    private final String tesseract;
    private final String asrUrl;

    public VideoPipeline(LibraryStore store, SearchEngine search, ObjectMapper mapper,
                         @Value("${app.ffmpeg-path:ffmpeg}") String ffmpeg,
                         @Value("${app.ffprobe-path:ffprobe}") String ffprobe,
                         @Value("${app.tesseract-path:tesseract}") String tesseract,
                         @Value("${app.asr-url:}") String asrUrl) {
        this.store = store; this.search = search; this.mapper = mapper;
        this.ffmpeg = ffmpeg; this.ffprobe = ffprobe; this.tesseract = tesseract; this.asrUrl = asrUrl;
    }

    public Video upload(MultipartFile upload, String title) throws IOException {
        if (upload == null || upload.isEmpty()) throw new IllegalArgumentException("请选择非空 MP4 文件。");
        String original = upload.getOriginalFilename();
        if (original == null || !original.toLowerCase().endsWith(".mp4")) throw new IllegalArgumentException("只接受 .mp4 文件。");
        String id = UUID.randomUUID().toString();
        String fileName = id + ".mp4";
        Path path = store.root().resolve("videos").resolve(fileName);
        try {
            upload.transferTo(path);
            byte[] header = new byte[12];
            try (var input = Files.newInputStream(path)) {
                if (input.read(header) < 12 || !new String(header, 4, 4, StandardCharsets.US_ASCII).equals("ftyp")) {
                    throw new IllegalArgumentException("文件内容不是有效的 MP4 容器。");
                }
            }
            Double duration = probeDuration(path);
            String safeTitle = title == null || title.isBlank() ? original : title.trim();
            Video video = new Video(id, safeTitle, fileName, duration, Instant.now().toString());
            store.addVideo(video);
            return video;
        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(path);
            throw ex;
        }
    }

    public List<Segment> importTranscript(String videoId, TranscriptRequest request) throws IOException {
        Video video = store.video(videoId);
        if (video == null) throw new IllegalArgumentException("视频不存在。");
        if (request == null || request.segments() == null || request.segments().isEmpty())
            throw new IllegalArgumentException("请提供至少一段带起止时间的字幕。");
        List<Segment> segments = new ArrayList<>();
        for (TranscriptItem item : request.segments()) {
            if (item == null || item.text() == null || item.text().isBlank() ||
                !Double.isFinite(item.startSeconds()) || !Double.isFinite(item.endSeconds()) ||
                item.startSeconds() < 0 || item.endSeconds() <= item.startSeconds())
                throw new IllegalArgumentException("字幕时间或文本无效。");
            if (video.durationSeconds() != null && item.endSeconds() > video.durationSeconds() + 2)
                throw new IllegalArgumentException("字幕结束时间超过视频时长。");
            String id = UUID.randomUUID().toString();
            String frame = extractFrame(video, item, id);
            String visualText = frame == null ? "" : recognizeFrame(frame);
            List<String> points = search.matchKnowledge(item.text() + " " + visualText);
            String summary = item.text().length() <= 70 ? item.text() : item.text().substring(0, 70) + "…";
            String type = item.text().contains("例题") || item.text().contains("例如") ? "例题讲解"
                : item.text().contains("易错") ? "易错点" : "课程讲解";
            segments.add(new Segment(id, videoId, item.startSeconds(), item.endSeconds(), item.text(),
                visualText, summary, points, 2, type, frame));
        }
        segments.sort(java.util.Comparator.comparingDouble(Segment::startSeconds));
        store.replaceSegments(videoId, segments);
        return segments;
    }

    public List<Segment> autoTranscribe(String videoId) throws Exception {
        Video video = store.video(videoId);
        if (video == null || video.fileName() == null) throw new IllegalArgumentException("找不到已上传的视频。");
        if (asrUrl.isBlank()) throw new IllegalStateException("尚未配置 ASR 地址。可先手工导入带时间戳字幕，或设置 app.asr-url。");
        Path audio = store.root().resolve("videos").resolve(video.id() + ".wav");
        try {
            boolean ok = run(List.of(ffmpeg, "-y", "-i", videoPath(video).toString(), "-vn", "-ac", "1", "-ar", "16000", audio.toString()), 600);
            if (!ok) throw new IllegalStateException("FFmpeg 音频提取失败；请检查安装和 app.ffmpeg-path。");
            HttpRequest request = HttpRequest.newBuilder(URI.create(asrUrl))
                .timeout(Duration.ofMinutes(20)).header("Content-Type", "audio/wav")
                .POST(HttpRequest.BodyPublishers.ofFile(audio)).build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("ASR 服务返回 " + response.statusCode());
            TranscriptRequest transcript = mapper.readValue(response.body(), TranscriptRequest.class);
            return importTranscript(videoId, transcript);
        } finally {
            Files.deleteIfExists(audio);
        }
    }

    public Path videoPath(Video video) { return store.root().resolve("videos").resolve(video.fileName()); }
    public Path framePath(String fileName) {
        if (fileName == null || !fileName.matches("[a-f0-9-]+\\.jpg")) throw new IllegalArgumentException("无效的图片名。");
        return store.root().resolve("frames").resolve(fileName);
    }
    public boolean isAsrConfigured() { return !asrUrl.isBlank(); }

    private String extractFrame(Video video, TranscriptItem item, String segmentId) {
        if (video.fileName() == null) return null;
        String name = segmentId + ".jpg";
        Path out = store.root().resolve("frames").resolve(name);
        double at = (item.startSeconds() + item.endSeconds()) / 2;
        boolean ok = run(List.of(ffmpeg, "-y", "-ss", String.valueOf(at), "-i", videoPath(video).toString(),
            "-frames:v", "1", "-q:v", "3", out.toString()), 120);
        return ok && Files.exists(out) ? name : null;
    }

    private String recognizeFrame(String fileName) {
        try {
            Process process = new ProcessBuilder(tesseract, framePath(fileName).toString(), "stdout", "-l", "chi_sim+eng")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0 ? text : "";
        } catch (Exception ex) { return ""; }
    }

    private Double probeDuration(Path video) {
        try {
            Process process = new ProcessBuilder(ffprobe, "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", video.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0 ? Double.parseDouble(value) : null;
        } catch (Exception ex) { return null; }
    }

    private boolean run(List<String> command, int timeoutSeconds) {
        try {
            Process process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) { process.destroyForcibly(); return false; }
            return process.exitValue() == 0;
        } catch (Exception ex) { return false; }
    }
}
