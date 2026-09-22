package edu.demo.mathvideo;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.demo.mathvideo.Domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectTest {
    @TempDir Path temp;

    private LibraryStore store() throws Exception {
        LibraryStore store = new LibraryStore(new ObjectMapper(), temp.toString());
        store.initialize();
        return store;
    }

    @Test void searchReturnsCorrectTimestampAndKnowledgePoint() throws Exception {
        LibraryStore store = store();
        SearchEngine search = new SearchEngine(store, new EmbeddingService(new ObjectMapper(), "", ""));
        SearchHit hit = search.search("根的判别式", 5).get(0);
        assertEquals("demo-3", hit.segmentId());
        assertEquals(1175, hit.startSeconds());
        assertTrue(hit.knowledgePoints().contains("根的判别式"));
    }

    @Test void planOrdersPrerequisitesBeforeTarget() throws Exception {
        SearchEngine search = new SearchEngine(store(), new EmbeddingService(new ObjectMapper(), "", ""));
        LearningPlan plan = search.plan(new PlanRequest("根的判别式", List.of("algebra", "square-root")));
        List<String> ids = plan.steps().stream().map(PlanStep::knowledgePointId).toList();
        assertFalse(ids.contains("algebra"));
        assertFalse(ids.contains("square-root"));
        assertTrue(ids.indexOf("quadratic-equation") < ids.indexOf("discriminant"));
        assertEquals("discriminant", ids.get(ids.size() - 1));
    }

    @Test void uploadedVideoAcceptsTranscriptAndPersists() throws Exception {
        LibraryStore store = store();
        SearchEngine search = new SearchEngine(store, new EmbeddingService(new ObjectMapper(), "", ""));
        VideoPipeline pipeline = new VideoPipeline(store, search, new ObjectMapper(),
            "tool-does-not-exist", "tool-does-not-exist", "tool-does-not-exist", "");
        byte[] mp4Header = new byte[]{0,0,0,12,'f','t','y','p','i','s','o','m'};
        Video video = pipeline.upload(new MockMultipartFile("file", "lesson.mp4", "video/mp4", mp4Header), "测试课程");
        assertNotNull(video.id());
        List<Segment> segments = pipeline.importTranscript(video.id(), new TranscriptRequest(List.of(
            new TranscriptItem(0, 12, "讲解根的判别式 Δ=b²-4ac。"))));
        assertEquals(1, segments.size());
        assertEquals(List.of("discriminant"), segments.get(0).knowledgePointIds());
        LibraryStore reloaded = new LibraryStore(new ObjectMapper(), temp.toString());
        reloaded.initialize();
        assertEquals(2, reloaded.videos().size());
        assertEquals(6, reloaded.segments().size());
    }
}
