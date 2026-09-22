package edu.demo.mathvideo;

import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Component
public class WorkJobs {
    @FunctionalInterface public interface ProgressTask { Object run(Consumer<String> progress)throws Exception; }
    public record Job(String id, String state, String message, Object result) {}
    private final Map<String,Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ExecutorService interactive = Executors.newSingleThreadExecutor();
    public String submit(String message, Callable<?> task) {
        return submitOn(executor,message,p->task.call());
    }
    public String submitInteractive(String message, Callable<?> task) {
        return submitOn(interactive,message,p->task.call());
    }
    public String submitProgress(String message,ProgressTask task){return submitOn(executor,message,task);}
    private String submitOn(ExecutorService pool,String message,ProgressTask task) {
        String id = UUID.randomUUID().toString();
        jobs.put(id, new Job(id,"queued","任务已提交，等待处理",null));
        pool.submit(() -> {
            jobs.put(id,new Job(id,"running",message,null));
            try { Object result = task.run(update->jobs.put(id,new Job(id,"running",update,null))); jobs.put(id,new Job(id,"done","已完成",result)); }
            catch (Exception ex) { jobs.put(id,new Job(id,"failed", ex.getMessage() == null ? "处理失败，请检查服务和文件" : ex.getMessage(),null)); }
        });
        return id;
    }
    public Job get(String id) { Job job = jobs.get(id); if (job == null) throw new IllegalArgumentException("任务不存在，服务重启后请重新操作"); return job; }
    @PreDestroy public void close() { executor.shutdownNow(); interactive.shutdownNow(); }
}
