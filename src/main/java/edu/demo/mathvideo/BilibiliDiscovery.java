package edu.demo.mathvideo;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

@Component
public class BilibiliDiscovery {
    public record Candidate(String url,String title,double duration){}
    private final PrecisionMedia media;private final ObjectMapper mapper;
    public BilibiliDiscovery(PrecisionMedia media,ObjectMapper mapper){this.media=media;this.mapper=mapper;}
    public List<Candidate> search(String query)throws Exception{
        String url="https://search.bilibili.com/all?keyword="+URLEncoder.encode(query,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20");
        try {
            var response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build().send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(25)).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==200){var candidates=parseHtml(response.body());if(!candidates.isEmpty())return candidates;}
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw ex;}catch(Exception ignored){}
        try {
            String output=media.run(List.of(media.tool("yt-dlp"),"--ignore-config","--flat-playlist","--dump-single-json","--no-warnings","--socket-timeout","15","--retries","1","bilisearch8:"+query),60);
            int start=output.indexOf('{');if(start<0)throw new IllegalStateException();JsonNode root=mapper.readTree(output.substring(start));List<Candidate> matches=new ArrayList<>();
            for(JsonNode item:root.path("entries")){String link=item.path("url").asText(item.path("webpage_url").asText(""));try{link=PrecisionMedia.bilibiliUrl(link);matches.add(new Candidate(link,item.path("title").asText("教学视频"),item.path("duration").asDouble(0)));}catch(Exception ignored){}}
            if(!matches.isEmpty())return matches;
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw ex;}catch(Exception ignored){}
        throw new IllegalStateException("本次没有取得可用的B站搜索结果，可能是平台访问限制或网络异常。可以稍后重试，或粘贴具体B站视频链接继续分析剪辑；不会绕过验证码或登录限制。");
    }
    static List<Candidate> parseHtml(String html){
        Pattern card=Pattern.compile("<a\\b[^>]*href=[\"']((?:https?:)?//(?:www\\.)?bilibili\\.com/video/(?:BV[A-Za-z0-9]+|av[0-9]+)/?)[\"'][^>]*>\\s*<h3\\b[^>]*title=[\"']([^\"']+)[\"'][^>]*>",Pattern.CASE_INSENSITIVE);
        Pattern duration=Pattern.compile("bili-video-card__stats__duration[^>]*>([0-9:]+)<");
        Matcher m=card.matcher(html);Map<String,Candidate> found=new LinkedHashMap<>();
        while(m.find()&&found.size()<12){String link=m.group(1);if(link.startsWith("//"))link="https:"+link;try{link=PrecisionMedia.bilibiliUrl(link);}catch(Exception ex){continue;}
            String preceding=html.substring(Math.max(0,m.start()-5000),m.start());Matcher dm=duration.matcher(preceding);double seconds=0;
            while(dm.find()){double value=0;for(String part:dm.group(1).split(":"))value=value*60+Integer.parseInt(part);seconds=value;}
            found.putIfAbsent(link,new Candidate(link,HtmlUtils.htmlUnescape(m.group(2)),seconds));}
        return new ArrayList<>(found.values());
    }
}
