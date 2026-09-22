package edu.demo.mathvideo;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.demo.mathvideo.Domain.KnowledgePoint;
import edu.demo.mathvideo.Domain.Segment;
import edu.demo.mathvideo.Domain.Video;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class LibraryStore {
    public static class State {
        public List<Video> videos = new ArrayList<>();
        public List<Segment> segments = new ArrayList<>();
        public List<KnowledgePoint> knowledgePoints = new ArrayList<>();
    }

    private final ObjectMapper mapper;
    private final Path root;
    private State state;

    public LibraryStore(ObjectMapper mapper, @Value("${app.data-dir:./data}") String dataDir) {
        this.mapper = mapper;
        this.root = Path.of(dataDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public synchronized void initialize() throws IOException {
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("videos"));
        Files.createDirectories(root.resolve("frames"));
        Path file = root.resolve("library.json");
        if (Files.exists(file)) {
            state = mapper.readValue(file.toFile(), State.class);
            if (state.videos == null) state.videos = new ArrayList<>();
            if (state.segments == null) state.segments = new ArrayList<>();
            if (state.knowledgePoints == null) state.knowledgePoints = new ArrayList<>();
        } else {
            state = new State();
            seed();
            save();
        }
    }

    public Path root() { return root; }
    public synchronized List<Video> videos() { return List.copyOf(state.videos); }
    public synchronized List<Segment> segments() { return List.copyOf(state.segments); }
    public synchronized List<KnowledgePoint> knowledgePoints() { return List.copyOf(state.knowledgePoints); }
    public synchronized Video video(String id) { return state.videos.stream().filter(v -> v.id().equals(id)).findFirst().orElse(null); }

    public synchronized void addVideo(Video video) throws IOException {
        state.videos.add(video);
        save();
    }

    public synchronized void replaceSegments(String videoId, List<Segment> segments) throws IOException {
        state.segments.removeIf(s -> s.videoId().equals(videoId));
        state.segments.addAll(segments);
        save();
    }

    public synchronized void updateVideoDuration(String videoId, Double duration) throws IOException {
        for (int i = 0; i < state.videos.size(); i++) {
            Video v = state.videos.get(i);
            if (v.id().equals(videoId)) {
                state.videos.set(i, new Video(v.id(), v.title(), v.fileName(), duration, v.createdAt()));
                save();
                return;
            }
        }
    }

    private void save() throws IOException {
        Path temp = root.resolve("library-" + UUID.randomUUID() + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), state);
        try {
            Files.move(temp, root.resolve("library.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temp, root.resolve("library.json"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void seed() {
        state.knowledgePoints.add(new KnowledgePoint("square-root", "平方根", List.of("平方根", "开平方"), List.of()));
        state.knowledgePoints.add(new KnowledgePoint("algebra", "整式运算", List.of("整式", "代数式", "移项"), List.of()));
        state.knowledgePoints.add(new KnowledgePoint("linear-equation", "一元一次方程", List.of("一元一次方程", "解方程"), List.of("algebra")));
        state.knowledgePoints.add(new KnowledgePoint("quadratic-equation", "一元二次方程", List.of("一元二次方程", "二次方程"), List.of("linear-equation", "square-root")));
        state.knowledgePoints.add(new KnowledgePoint("factorization", "因式分解", List.of("因式分解", "提公因式", "十字相乘"), List.of("algebra")));
        state.knowledgePoints.add(new KnowledgePoint("formula-method", "求根公式", List.of("求根公式", "公式法"), List.of("quadratic-equation", "square-root")));
        state.knowledgePoints.add(new KnowledgePoint("discriminant", "根的判别式", List.of("判别式", "根的判别式", "delta", "Δ"), List.of("quadratic-equation", "formula-method")));
        state.knowledgePoints.add(new KnowledgePoint("root-relations", "根与系数的关系", List.of("根与系数", "韦达定理"), List.of("quadratic-equation")));
        state.knowledgePoints.add(new KnowledgePoint("quadratic-app", "一元二次方程应用题", List.of("应用题", "实际问题"), List.of("quadratic-equation", "factorization")));

        state.videos.add(new Video("demo-video", "示例：一元二次方程（文字演示）", null, 42.0 * 60, "2026-09-19T00:00:00Z"));
        state.segments.add(new Segment("demo-1", "demo-video", 205, 642, "一元二次方程的一般形式是 ax²+bx+c=0，其中 a 不等于 0。先认识二次项系数、一次项系数和常数项。", "", "一元二次方程的概念与一般形式", List.of("quadratic-equation"), 1, "概念讲解", null));
        state.segments.add(new Segment("demo-2", "demo-video", 642, 1175, "利用求根公式 x=(-b±√(b²-4ac))/(2a) 求解一元二次方程，代入前应先确认 a、b、c。", "", "求根公式与公式法", List.of("formula-method"), 2, "方法讲解", null));
        state.segments.add(new Segment("demo-3", "demo-video", 1175, 1690, "根的判别式 Δ=b²-4ac。Δ>0 有两个不相等实数根，Δ=0 有两个相等实数根，Δ<0 没有实数根。", "", "根的判别式及根的情况", List.of("discriminant"), 2, "重点讲解", null));
        state.segments.add(new Segment("demo-4", "demo-video", 1690, 2126, "例题：判断 x²-5x+6=0 的根的情况。a=1、b=-5、c=6，Δ=25-24=1，大于零，因此有两个不相等实数根。", "", "判别式例题", List.of("discriminant"), 2, "例题讲解", null));
        state.segments.add(new Segment("demo-5", "demo-video", 2126, 2480, "易错点：计算 Δ 时要给负的 b 加括号，且注意 Δ=0 对应两个相等实数根，不是没有根。", "", "判别式常见易错点", List.of("discriminant"), 2, "易错点", null));
    }
}
