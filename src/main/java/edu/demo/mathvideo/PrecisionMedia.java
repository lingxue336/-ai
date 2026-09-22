package edu.demo.mathvideo;

import com.fasterxml.jackson.databind.*;
import edu.demo.mathvideo.PrecisionModels.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

@Component
public class PrecisionMedia {
    private final PrecisionStore store;
    private final SkillCatalog catalog;
    private final ObjectMapper mapper;
    private final Path tools;
    public PrecisionMedia(PrecisionStore store, SkillCatalog catalog, ObjectMapper mapper,
                          @Value("${app.media-tools-dir:./tools}") String toolsDir) {
        this.store=store; this.catalog=catalog; this.mapper=mapper; this.tools=Path.of(toolsDir).toAbsolutePath();
    }
    public String tool(String name) {
        Path file = tools.resolve(name + (System.getProperty("os.name").toLowerCase().contains("windows") ? ".exe" : ""));
        return Files.isRegularFile(file) ? file.toString() : name;
    }
    public boolean available(String name) { try { run(List.of(tool(name),name.equals("yt-dlp")?"--version":"-version"),10); return true; } catch(Exception ex) { return false; } }
    public Path source(Asset asset) { return store.root().resolve("sources").resolve(asset.fileName()); }
    public Path clipFile(Clip clip) { return store.root().resolve("clips").resolve(clip.fileName()); }
    public byte[] storyboard(String assetId,double from,double to) throws Exception {
        Asset asset=store.asset(assetId);
        if(!Double.isFinite(from)||!Double.isFinite(to)||from<0||to<=from||from>=asset.duration())
            throw new IllegalArgumentException("视频画面抽取区间无效，请检查字幕时间是否与视频一致");
        if(!Files.isRegularFile(source(asset)))throw new IllegalStateException("找不到原视频文件，无法读取画面，请重新导入视频");
        double end=Math.min(to,asset.duration());
        int columns=2,cellWidth=640,imageHeight=360,labelHeight=36,cellHeight=imageHeight+labelHeight;
        BufferedImage board=new BufferedImage(columns*cellWidth,3*cellHeight,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=board.createGraphics();
        try {
            graphics.setColor(Color.WHITE);graphics.fillRect(0,0,board.getWidth(),board.getHeight());
            graphics.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18));
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            for(int i=0;i<6;i++) {
                double sampledAt=from+(end-from)*(i+0.5)/6;
                Path frame=Files.createTempFile(store.root().resolve("logs"),"storyboard-frame-",".jpg");
                try {
                    run(List.of(tool("ffmpeg"),"-hide_banner","-nostdin","-y","-ss",Double.toString(sampledAt),
                        "-i",source(asset).toString(),"-map","0:v:0","-an","-sn","-dn","-frames:v","1",
                        "-vf","scale=640:360:force_original_aspect_ratio=decrease","-q:v","2","-update","1",frame.toString()),60);
                    BufferedImage image=ImageIO.read(frame.toFile());
                    if(image==null)throw new IllegalStateException("抽取位置没有可读取的视频画面");
                    int x=(i%columns)*cellWidth,y=(i/columns)*cellHeight;
                    graphics.setColor(new Color(237,241,244));graphics.fillRect(x,y,cellWidth,labelHeight);
                    graphics.setColor(new Color(24,38,49));
                    graphics.drawString(String.format(Locale.ROOT,"Frame %d  |  t = %.3f s",i+1,sampledAt),x+12,y+25);
                    double scale=Math.min((double)cellWidth/image.getWidth(),(double)imageHeight/image.getHeight());
                    int width=Math.max(1,(int)Math.round(image.getWidth()*scale)),height=Math.max(1,(int)Math.round(image.getHeight()*scale));
                    graphics.drawImage(image,x+(cellWidth-width)/2,y+labelHeight+(imageHeight-height)/2,width,height,null);
                    image.flush();
                } finally { Files.deleteIfExists(frame); }
            }
            ByteArrayOutputStream jpeg=new ByteArrayOutputStream();
            if(!ImageIO.write(board,"jpg",jpeg))throw new IllegalStateException("无法生成视频画面预览");
            return jpeg.toByteArray();
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();throw new IllegalStateException("视频画面读取已中断，请稍后重试",ex);
        } catch(Exception ex) {
            throw new IllegalStateException("无法读取视频画面，请确认原视频能正常播放且 FFmpeg 可用；可先手工预览打点。",ex);
        } finally { graphics.dispose();board.flush(); }
    }
    private Path whisper() {
        try(var paths=Files.walk(tools.resolve("whisper"),4)) {
            return paths.filter(p->p.getFileName().toString().equals("whisper-cli.exe")).findFirst().orElse(tools.resolve("whisper/whisper-cli.exe"));
        } catch(Exception ex) { return tools.resolve("whisper/whisper-cli.exe"); }
    }
    private Path speechModel() { return tools.getParent().resolve("models/ggml-small.bin"); }
    public boolean asrAvailable() { return Files.isRegularFile(whisper())&&Files.isRegularFile(speechModel()); }
    public Asset transcribe(String id) throws Exception {
        Asset asset=store.asset(id);
        if(!asrAvailable())throw new IllegalStateException("本地语音模型尚未安装完成，请稍后再试或上传字幕");
        Path wav=store.root().resolve("sources").resolve(id+"-asr.wav");
        Path prefix=store.root().resolve("sources").resolve(id+"-asr");
        try {
            run(List.of(tool("ffmpeg"),"-hide_banner","-nostdin","-y","-i",source(asset).toString(),"-vn","-ar","16000","-ac","1","-c:a","pcm_s16le",wav.toString()),300);
            Path working=tools.getParent().toAbsolutePath().normalize();
            run(List.of(whisper().toString(),"-m",working.relativize(speechModel().toAbsolutePath().normalize()).toString(),"-f",working.relativize(wav.toAbsolutePath().normalize()).toString(),"-l","zh","-t","8","-osrt","-of",working.relativize(prefix.toAbsolutePath().normalize()).toString()),Math.max(600,(int)asset.duration()*5),working);
            List<Cue> cues=SubtitleParser.parse(Files.readString(Path.of(prefix+".srt")),mapper).stream()
                .filter(c->c.start()<asset.duration()).map(c->new Cue(c.start(),Math.min(c.end(),asset.duration()),c.text())).toList();
            if(cues.isEmpty())throw new IllegalStateException("未识别到有效课堂语音，请上传字幕或手工打点");
            Asset updated=new Asset(asset.id(),asset.title(),asset.sourceUrl(),asset.author(),asset.fileName(),asset.duration(),asset.grade(),cues,asset.createdAt());
            store.putAsset(updated);return updated;
        } finally { Files.deleteIfExists(wav); }
    }
    public double duration(Path path) throws Exception {
        String out = run(List.of(tool("ffprobe"),"-v","error","-show_entries","format=duration","-of","default=noprint_wrappers=1:nokey=1",path.toString()),30);
        double value = Double.parseDouble(out.trim());
        if (!Double.isFinite(value) || value<=0) throw new IllegalArgumentException("无法读取视频时长");
        return value;
    }
    public Asset upload(MultipartFile file,String title,String sourceUrl,String author,int grade) throws Exception {
        validateGrade(grade);
        if (file.isEmpty() || file.getSize()>2_000_000_000L) throw new IllegalArgumentException("请上传小于 2GB 的教学视频");
        String id=UUID.randomUUID().toString();
        Path target=store.root().resolve("sources").resolve(id+".mp4");
        Path uploaded=store.root().resolve("sources").resolve(id+".upload");
        file.transferTo(uploaded);
        try {
            JsonNode probe=mapper.readTree(run(List.of(tool("ffprobe"),"-v","error","-show_streams","-show_format","-of","json",uploaded.toString()),30));
            boolean browserSafe=probe.path("format").path("format_name").asText().contains("mp4"),hasVideo=false;
            for(JsonNode stream:probe.path("streams")) {
                if(stream.path("codec_type").asText().equals("video")){hasVideo=true;browserSafe&=stream.path("codec_name").asText().equals("h264");}
                if(stream.path("codec_type").asText().equals("audio"))browserSafe&=stream.path("codec_name").asText().equals("aac");
            }
            if(!hasVideo)throw new IllegalArgumentException("文件没有视频画面");
            if(browserSafe)Files.move(uploaded,target);
            else run(List.of(tool("ffmpeg"),"-hide_banner","-nostdin","-y","-i",uploaded.toString(),"-map","0:v:0","-map","0:a:0?","-c:v","libx264","-preset","veryfast","-crf","23","-pix_fmt","yuv420p","-c:a","aac","-movflags","+faststart",target.toString()),1800);
            double duration=duration(target);
            Asset asset=new Asset(id, title==null||title.isBlank()?"未命名教学视频":title.trim(),sourceUrl==null?"":sourceUrl,
                author==null?"":author,id+".mp4",duration,grade,List.of(),Instant.now().toString());
            store.putAsset(asset); return asset;
        } catch(Exception ex) { Files.deleteIfExists(target); throw new IllegalArgumentException("视频读取或转换失败，请使用有效视频文件，并确认 FFmpeg 可用。",ex); }
        finally { Files.deleteIfExists(uploaded); }
    }
    public static String bilibiliUrl(String input) {
        URI uri;
        try { uri=URI.create(input.trim()); } catch(Exception ex) { throw new IllegalArgumentException("B站链接格式无效"); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo()!=null || uri.getPort()!=-1 ||
            !("www.bilibili.com".equalsIgnoreCase(uri.getHost()) || "bilibili.com".equalsIgnoreCase(uri.getHost())) ||
            !uri.getPath().matches("/video/(BV[a-zA-Z0-9]+|av[0-9]+)/?"))
            throw new IllegalArgumentException("请粘贴 https://www.bilibili.com/video/BV... 形式的视频链接");
        String page="";
        if (uri.getQuery()!=null) for(String item:uri.getQuery().split("&")) if(item.matches("p=[1-9][0-9]{0,3}")) page="?"+item;
        return "https://www.bilibili.com"+uri.getPath()+page;
    }
    public Asset importBilibili(String input,int grade) throws Exception {
        return importBilibili(input,grade,false);
    }
    public Asset importBilibili(String input,int grade,boolean bounded) throws Exception {
        validateGrade(grade); String url=bilibiliUrl(input), id=UUID.randomUUID().toString();
        Path base=store.root().resolve("sources");
        run(List.of(tool("yt-dlp"),"--ignore-config","--no-playlist","--socket-timeout","20","--retries","2",
            "--max-filesize",bounded?"512M":"2G","--match-filter",bounded?"duration <= 1200":"duration >= 0","--ffmpeg-location",Path.of(tool("ffmpeg")).toAbsolutePath().getParent().toString(),
            "-f","bv*[height<=720]+ba/b[height<=720]/b","--merge-output-format","mp4","--remux-video","mp4",
            "--write-info-json","--write-subs","--write-auto-subs","--sub-langs","zh.*,ai-zh","--sub-format","srt/vtt/best",
            "-o",base.resolve(id+".%(ext)s").toString(),"--",url),1200);
        Path mp4=base.resolve(id+".mp4");
        if (!Files.exists(mp4)) throw new IllegalStateException("B站未返回可用视频，可能需要登录或该视频不允许直接获取。可改用本地视频上传。");
        JsonNode info=mapper.readTree(base.resolve(id+".info.json").toFile());
        List<Cue> cues=List.of();
        try(var files=Files.list(base)) {
            for(Path sub:files.filter(p->p.getFileName().toString().startsWith(id+".") && !p.toString().endsWith(".info.json") &&
                (p.toString().endsWith(".srt")||p.toString().endsWith(".vtt")||p.toString().endsWith(".json"))).toList()) {
                try { cues=SubtitleParser.parse(Files.readString(sub),mapper); break; } catch(Exception ignored) {}
            }
        }
        Asset asset=new Asset(id,info.path("title").asText("B站教学视频"),url,info.path("uploader").asText(""),id+".mp4",duration(mp4),grade,cues,Instant.now().toString());
        store.putAsset(asset); return asset;
    }
    public Asset subtitles(String id,String text) throws Exception {
        Asset old=store.asset(id); List<Cue> cues=SubtitleParser.parse(text,mapper);
        if(cues.stream().anyMatch(c->c.end()>old.duration()+1)) throw new IllegalArgumentException("字幕时间超过视频时长，请确认字幕与视频一致");
        Asset asset=new Asset(old.id(),old.title(),old.sourceUrl(),old.author(),old.fileName(),old.duration(),old.grade(),cues,old.createdAt());
        store.putAsset(asset); return asset;
    }
    public Clip publish(String assetId,Mark mark) throws Exception {
        return publishInternal(assetId,mark,"teacher");
    }
    public Clip publishAutomatic(String assetId,Mark mark) throws Exception {
        return publishInternal(assetId,new Mark(mark.title(),mark.skillId(),mark.start(),mark.end(),mark.explanation(),true),"automatic-boundary-checked");
    }
    private Clip publishInternal(String assetId,Mark mark,String reviewMode) throws Exception {
        Asset asset=store.asset(assetId); catalog.get(mark.skillId());
        if(!mark.reviewed()) throw new IllegalArgumentException("请先预览并确认片段内容与卡点一致");
        if(mark.title()==null||mark.title().isBlank()||mark.explanation()==null||mark.explanation().isBlank()) throw new IllegalArgumentException("请填写片段标题及具体解决的问题");
        double length=mark.end()-mark.start();
        if(!Double.isFinite(mark.start())||!Double.isFinite(mark.end())||mark.start()<0||mark.end()>asset.duration()+0.05||length<5||length>360)
            throw new IllegalArgumentException("片段需为 5 秒至 6 分钟，且起止位置必须在原视频内");
        String id=UUID.randomUUID().toString(); Path pending=store.root().resolve("clips").resolve(id+".partial.mp4");
        Path output=store.root().resolve("clips").resolve(id+".mp4");
        try {
            run(List.of(tool("ffmpeg"),"-hide_banner","-nostdin","-y","-ss",Double.toString(mark.start()),"-i",source(asset).toString(),
                "-t",Double.toString(length),"-map","0:v:0","-map","0:a:0?","-c:v","libx264","-preset","veryfast","-crf","23",
                "-pix_fmt","yuv420p","-c:a","aac","-b:a","128k","-movflags","+faststart","-avoid_negative_ts","make_zero",pending.toString()),600);
            double actual=duration(pending);
            if(Math.abs(actual-length)>0.6) throw new IllegalStateException("裁剪后的时长校验未通过，请调整打点位置");
            Files.move(pending,output);
            Clip clip=new Clip(id,assetId,mark.title().trim(),mark.skillId(),mark.start(),mark.end(),asset.grade(),mark.explanation().trim(),id+".mp4",actual,Instant.now().toString(),reviewMode);
            store.putClip(clip); return clip;
        } finally { Files.deleteIfExists(pending); }
    }
    private void validateGrade(int grade) { if(grade<7||grade>9)throw new IllegalArgumentException("目前只支持初一、初二、初三数学"); }
    public String run(List<String> command,int seconds) throws Exception {
        return run(command,seconds,null);
    }
    private String run(List<String> command,int seconds,Path working) throws Exception {
        Path log=store.root().resolve("logs").resolve(UUID.randomUUID()+".log");
        Process process;
        try { ProcessBuilder builder=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
            if(working!=null)builder.directory(working.toFile());
            builder.environment().put("PYTHONIOENCODING","utf-8");builder.environment().put("PYTHONUTF8","1");process=builder.start(); }
        catch(Exception ex) { throw new IllegalStateException("无法启动 "+Path.of(command.get(0)).getFileName()+"，请检查 tools 目录中的程序"); }
        if(!process.waitFor(seconds,TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("处理超时，请缩短视频或检查网络"); }
        String text=new String(Files.readAllBytes(log),java.nio.charset.StandardCharsets.UTF_8);
        if(process.exitValue()!=0) throw new IllegalStateException("媒体处理失败："+text.substring(Math.max(0,text.length()-700)));
        return text;
    }
}
