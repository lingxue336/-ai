package edu.demo.mathvideo;

import edu.demo.mathvideo.Domain.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final LibraryStore store;
    private final VideoPipeline pipeline;
    private final SearchEngine search;
    private final AnswerService answers;
    private final EmbeddingService embeddings;

    public ApiController(LibraryStore store, VideoPipeline pipeline, SearchEngine search,
                         AnswerService answers, EmbeddingService embeddings) {
        this.store = store; this.pipeline = pipeline; this.search = search;
        this.answers = answers; this.embeddings = embeddings;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("status", "ready", "videos", store.videos().size(), "segments", store.segments().size(),
            "asrConfigured", pipeline.isAsrConfigured(), "modelConfigured", answers.isModelConfigured(),
            "embeddingConfigured", embeddings.isConfigured());
    }

    @GetMapping("/catalog")
    public Map<String, Object> catalog() {
        return Map.of("videos", store.videos(), "knowledgePoints", store.knowledgePoints(), "segments", store.segments());
    }

    @PostMapping(value = "/videos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Video upload(@RequestPart("file") MultipartFile file, @RequestParam(value = "title", required = false) String title) throws Exception {
        return pipeline.upload(file, title);
    }

    @PostMapping("/videos/{id}/transcript")
    public List<Segment> importTranscript(@PathVariable String id, @RequestBody TranscriptRequest request) throws Exception {
        return pipeline.importTranscript(id, request);
    }

    @PostMapping("/videos/{id}/auto-transcribe")
    public List<Segment> autoTranscribe(@PathVariable String id) throws Exception {
        return pipeline.autoTranscribe(id);
    }

    @GetMapping("/videos/{id}/file")
    public ResponseEntity<Resource> videoFile(@PathVariable String id) {
        Video video = store.video(id);
        if (video == null || video.fileName() == null) return ResponseEntity.notFound().build();
        Path path = pipeline.videoPath(video);
        if (!Files.isRegularFile(path)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.valueOf("video/mp4"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + video.fileName() + "\"")
            .body(new FileSystemResource(path));
    }

    @GetMapping("/frames/{fileName}")
    public ResponseEntity<Resource> frame(@PathVariable String fileName) {
        Path path = pipeline.framePath(fileName);
        if (!Files.isRegularFile(path)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(new FileSystemResource(path));
    }

    @GetMapping("/search")
    public List<SearchHit> search(@RequestParam String q, @RequestParam(defaultValue = "5") int topK) {
        return search.search(q, topK);
    }

    @GetMapping("/ask")
    public Answer ask(@RequestParam String q) { return answers.answer(q); }

    @PostMapping("/plan")
    public LearningPlan plan(@RequestBody PlanRequest request) { return search.plan(request); }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> badRequest(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
