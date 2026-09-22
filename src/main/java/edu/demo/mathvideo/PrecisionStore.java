package edu.demo.mathvideo;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.demo.mathvideo.PrecisionModels.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

@Component
public class PrecisionStore {
    public static class State {
        public List<Asset> assets = new ArrayList<>();
        public List<Clip> clips = new ArrayList<>();
        public List<Diagnosis> diagnoses = new ArrayList<>();
        public List<Feedback> feedback = new ArrayList<>();
    }
    private final ObjectMapper mapper;
    private final Path root;
    private State state;
    public PrecisionStore(ObjectMapper mapper, LibraryStore legacy) { this.mapper = mapper; root = legacy.root().resolve("precision"); }
    @PostConstruct public synchronized void init() throws IOException {
        for (String dir : List.of("sources", "clips", "photos", "logs")) Files.createDirectories(root.resolve(dir));
        Path file = root.resolve("library.json");
        state = Files.exists(file) ? mapper.readValue(file.toFile(), State.class) : new State();
    }
    public Path root() { return root; }
    public synchronized List<Asset> assets() { return List.copyOf(state.assets); }
    public synchronized List<Clip> clips() { return List.copyOf(state.clips); }
    public synchronized List<Diagnosis> diagnoses() { return List.copyOf(state.diagnoses); }
    public synchronized List<Feedback> feedback() { return List.copyOf(state.feedback); }
    public synchronized Asset asset(String id) { return state.assets.stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("视频不存在")); }
    public synchronized Clip clip(String id) { return state.clips.stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("短视频不存在")); }
    public synchronized Diagnosis diagnosis(String id) { return state.diagnoses.stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("请先上传题目照片")); }
    public synchronized void putAsset(Asset asset) throws IOException { state.assets.removeIf(v -> v.id().equals(asset.id())); state.assets.add(asset); save(); }
    public synchronized void putClip(Clip clip) throws IOException { state.clips.add(clip); save(); }
    public synchronized void putDiagnosis(Diagnosis diagnosis) throws IOException { state.diagnoses.add(diagnosis); save(); }
    public synchronized void addFeedback(Feedback feedback) throws IOException { state.feedback.add(feedback); save(); }
    private void save() throws IOException {
        Path temp = root.resolve(UUID.randomUUID() + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), state);
        try { Files.move(temp, root.resolve("library.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(temp, root.resolve("library.json"), StandardCopyOption.REPLACE_EXISTING); }
    }
}
