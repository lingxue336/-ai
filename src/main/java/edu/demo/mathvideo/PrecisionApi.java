package edu.demo.mathvideo;

import edu.demo.mathvideo.PrecisionModels.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/v2")
public class PrecisionApi {
    private final PrecisionStore store;private final PrecisionService service;private final PrecisionMedia media;
    private final ModelGateway model;private final SkillCatalog catalog;private final WorkJobs jobs;private final OnlineLessons online;
    public PrecisionApi(PrecisionStore store,PrecisionService service,PrecisionMedia media,ModelGateway model,SkillCatalog catalog,WorkJobs jobs,OnlineLessons online){
        this.store=store;this.service=service;this.media=media;this.model=model;this.catalog=catalog;this.jobs=jobs;this.online=online;
    }
    @GetMapping("/health") public Map<String,Object> health(){return Map.of("model",model.health(),"clipCount",store.clips().size(),"assetCount",store.assets().size(),"ffmpeg",media.available("ffmpeg"),"bilibiliImporter",media.available("yt-dlp"),"asr",media.asrAvailable());}
    @GetMapping("/skills") public List<Skill> skills(){return catalog.all();}
    @GetMapping("/jobs/{id}") public WorkJobs.Job job(@PathVariable String id){return jobs.get(id);}
    @PostMapping("/diagnoses") public Map<String,String> diagnose(@RequestPart("photo")MultipartFile photo,@RequestParam int grade,@RequestParam(defaultValue="")String note)throws Exception{
        if(photo.getSize()>15_000_000)throw new IllegalArgumentException("照片大小不能超过15MB");
        if(!model.configured())throw new IllegalStateException("请先在老师工作台连接本地视觉模型");
        byte[] bytes=photo.getBytes();return Map.of("jobId",jobs.submitInteractive("正在读题并分析作答痕迹",()->service.analyze(bytes,grade,note)));
    }
    @GetMapping("/history") public Map<String,Object> history(){return Map.of("diagnoses",store.diagnoses(),"feedback",store.feedback());}
    @GetMapping("/diagnoses/{id}/photo") public ResponseEntity<Resource> photo(@PathVariable String id){store.diagnosis(id);return resource(store.root().resolve("photos").resolve(id+".jpg"),MediaType.IMAGE_JPEG);}
    public record RecommendRequest(String diagnosisId,String skillId){}
    public record OnlineRequest(String diagnosisId,String skillId,String query,String sourceUrl){}
    @PostMapping("/online-lessons")public Map<String,String> online(@RequestBody OnlineRequest request){return Map.of("jobId",online.start(request.diagnosisId(),request.skillId(),request.query(),request.sourceUrl()));}
    @PostMapping("/recommendations") public Map<String,Object> recommend(@RequestBody RecommendRequest request)throws Exception{List<Lesson> lessons=service.recommend(request.diagnosisId(),request.skillId());return Map.of("lessons",lessons,"message",lessons.isEmpty()?"视频库暂时没有覆盖这个具体卡点的合适短片，请老师补充对应讲解。":"以下独立短片已结合本题卡点筛选，请边看边核对自己的解题步骤。");}
    public record FeedbackRequest(String diagnosisId,String clipId,String result){}
    @PostMapping("/feedback") public Map<String,String> feedback(@RequestBody FeedbackRequest request)throws Exception{service.feedback(request.diagnosisId(),request.clipId(),request.result());return Map.of("message",request.result().equals("understood")?"已记录掌握情况，可以回到原题再试一次。":"已记录，我们会保留这道题供老师进一步定位卡点。");}
    @GetMapping("/clips/{id}/play") public ResponseEntity<Resource> clip(@PathVariable String id){return resource(media.clipFile(store.clip(id)),MediaType.valueOf("video/mp4"));}
    @GetMapping("/teacher/library") public Map<String,Object> library(){return Map.of("assets",store.assets(),"clips",store.clips());}
    @PostMapping("/teacher/videos") public Asset upload(@RequestPart("file")MultipartFile file,@RequestParam String title,@RequestParam int grade,@RequestParam(defaultValue="")String sourceUrl,@RequestParam(defaultValue="")String author)throws Exception{return media.upload(file,title,sourceUrl,author,grade);}
    public record BilibiliRequest(String url,int grade){}
    @PostMapping("/teacher/bilibili") public Map<String,String> bilibili(@RequestBody BilibiliRequest request){String url=PrecisionMedia.bilibiliUrl(request.url());return Map.of("jobId",jobs.submit("正在导入B站视频及可用字幕",()->media.importBilibili(url,request.grade())));}
    @GetMapping("/teacher/videos/{id}/play")public ResponseEntity<Resource> source(@PathVariable String id){return resource(media.source(store.asset(id)),MediaType.valueOf("video/mp4"));}
    @PostMapping("/teacher/videos/{id}/subtitles") public Asset subtitles(@PathVariable String id,@RequestPart("file")MultipartFile file)throws Exception{if(file.getSize()>10_000_000)throw new IllegalArgumentException("字幕文件过大");return media.subtitles(id,new String(file.getBytes(),StandardCharsets.UTF_8));}
    @PostMapping("/teacher/videos/{id}/suggest-marks") public Map<String,String> suggest(@PathVariable String id){return Map.of("jobId",jobs.submit("正在按具体知识卡点生成打点建议",()->service.suggestMarks(id)));}
    @PostMapping("/teacher/videos/{id}/transcribe") public Map<String,String> transcribe(@PathVariable String id){store.asset(id);return Map.of("jobId",jobs.submit("正在本机把视频语音转换成带时间戳的字幕",()->media.transcribe(id)));}
    @PostMapping("/teacher/videos/{id}/clips") public Map<String,String> publish(@PathVariable String id,@RequestBody Mark mark){store.asset(id);return Map.of("jobId",jobs.submit("正在精确裁剪并校验短视频",()->media.publish(id,mark)));}
    public record ModelRequest(String provider,String baseUrl,String model,String apiKey){}
    @PostMapping("/teacher/model")public Map<String,Object> configure(@RequestBody ModelRequest request)throws Exception{model.configure(new ModelGateway.Settings(request.provider(),request.baseUrl(),request.model()),request.apiKey());return model.publicSettings();}
    private ResponseEntity<Resource> resource(Path path,MediaType type){if(!Files.isRegularFile(path))return ResponseEntity.notFound().build();return ResponseEntity.ok().contentType(type).header("Cache-Control","no-store").body(new FileSystemResource(path));}
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class})public ResponseEntity<Map<String,String>> bad(Exception ex){return ResponseEntity.badRequest().body(Map.of("error",ex.getMessage()));}
    @ExceptionHandler(Exception.class)public ResponseEntity<Map<String,String>> failed(Exception ex){return ResponseEntity.internalServerError().body(Map.of("error","处理失败，请检查文件内容或后台日志。"));}
}
