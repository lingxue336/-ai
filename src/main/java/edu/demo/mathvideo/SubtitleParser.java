package edu.demo.mathvideo;

import com.fasterxml.jackson.databind.*;
import edu.demo.mathvideo.PrecisionModels.Cue;
import java.util.*;
import java.util.regex.*;

public final class SubtitleParser {
    private SubtitleParser() {}
    private static final Pattern TIME = Pattern.compile("(?:(\\d+):)?(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(?:(\\d+):)?(\\d{2}):(\\d{2})[,.](\\d{3})[^\\n]*\\n([\\s\\S]*?)(?=\\n\\s*\\n|$)");
    public static List<Cue> parse(String text, ObjectMapper mapper) throws Exception {
        text = text.replace("\ufeff", "").replace("\r", "").trim();
        List<Cue> cues = new ArrayList<>();
        if (text.startsWith("{") || text.startsWith("[")) {
            JsonNode root = mapper.readTree(text);
            JsonNode body = root.has("body") ? root.path("body") : root.has("segments") ? root.path("segments") : root;
            if (body.isArray()) for (JsonNode item : body) {
                double start = item.has("from") ? item.path("from").asDouble() : item.path("startSeconds").asDouble();
                double end = item.has("to") ? item.path("to").asDouble() : item.path("endSeconds").asDouble();
                String content = item.has("content") ? item.path("content").asText() : item.path("text").asText();
                cues.add(new Cue(start,end,content));
            }
        } else {
            Matcher matcher = TIME.matcher(text);
            while (matcher.find()) cues.add(new Cue(seconds(matcher,1), seconds(matcher,5), matcher.group(9).replaceAll("<[^>]+>","").replace('\n',' ').trim()));
        }
        cues.removeIf(c -> c.text().isBlank());
        if (cues.isEmpty()) throw new IllegalArgumentException("没有读到字幕时间轴，请上传 SRT、VTT 或 B站字幕 JSON 文件。");
        for (Cue cue : cues) if (!Double.isFinite(cue.start()) || !Double.isFinite(cue.end()) || cue.start() < 0 || cue.end() <= cue.start()) throw new IllegalArgumentException("字幕中存在无效时间");
        cues.sort(Comparator.comparingDouble(Cue::start));
        return List.copyOf(cues);
    }
    private static double seconds(Matcher m,int offset) {
        return (m.group(offset)==null?0:Integer.parseInt(m.group(offset))*3600) + Integer.parseInt(m.group(offset+1))*60 + Integer.parseInt(m.group(offset+2)) + Integer.parseInt(m.group(offset+3))/1000.0;
    }
}
