package edu.demo.mathvideo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.demo.mathvideo.PrecisionModels.*;
import java.nio.file.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class OnlineLessonsTest {
    @TempDir Path temp;
    @Test void actualIntervalCanVetoBroadVideoMatchAndCorrectBoundaries()throws Exception{
        ObjectMapper mapper=new ObjectMapper();PrecisionStore store=mock(PrecisionStore.class);when(store.root()).thenReturn(temp);Files.createDirectory(temp.resolve("logs"));
        PrecisionMedia media=mock(PrecisionMedia.class);when(media.storyboard(anyString(),anyDouble(),anyDouble())).thenReturn(new byte[]{1});
        ModelGateway model=mock(ModelGateway.class);SkillCatalog catalog=new SkillCatalog();
        var broad=mapper.readTree("{\"matched\":true,\"startCue\":0,\"endCue\":0,\"title\":\"平方公式\",\"explanation\":\"此处的宽泛描述不能作为审核证据\"}");
        var corrected=mapper.readTree("{\"matched\":true,\"startCue\":1,\"endCue\":1,\"title\":\"负号公式\",\"explanation\":\"修正范围后的原始描述\"}");
        var veto=mapper.readTree("{\"matched\":false,\"explanation\":\"只讲正号，没有负号推导\"}");
        var pass=mapper.readTree("{\"matched\":true,\"explanation\":\"区间内确实讲解了负号展开的推导\"}");
        when(model.complete(anyString(),any(byte[].class),any())).thenReturn(broad,veto,corrected,pass);
        var asset=new Asset("a","全课","","","a.mp4",60,9,List.of(new Cue(0,10,"首平方尾平方"),new Cue(10,30,"负号展开为a方减2ab加b方")),"now");
        var diagnosis=new Diagnosis("d","x²-4x+4=0","写成(x+2)²","符号错误",true,true,9,List.of("quadratic-completing-square"),"","now");
        var online=new OnlineLessons(store,media,null,catalog,model,mapper,null,null);
        Mark mark=online.focusedMark(asset,diagnosis,catalog.get("quadratic-completing-square"));
        assertNotNull(mark);assertEquals(10,mark.start());assertEquals(30,mark.end());assertEquals(pass.path("explanation").asText(),mark.explanation());
        var prompts=org.mockito.ArgumentCaptor.forClass(String.class);verify(model,times(4)).complete(prompts.capture(),any(byte[].class),any());
        assertFalse(prompts.getAllValues().get(1).contains("此处的宽泛描述"));
        assertFalse(prompts.getAllValues().get(1).contains("负号展开为a方减2ab加b方"),"复核不能读取选中区间之外的字幕");
        when(model.complete(anyString(),any(byte[].class),any())).thenReturn(broad,veto,broad,veto);
        assertNull(online.focusedMark(asset,diagnosis,catalog.get("quadratic-completing-square")),"两次边界复核不通过就不发布");
    }
    @Test void parsesOnlyLinkedBilibiliCardsAndKeepsDuration(){
        String html="<span class=\"bili-video-card__stats__duration\">03:16</span><a href=\"//www.bilibili.com/video/BV18x411x79x/\"><h3 title=\"根的判别式 &amp; 根的情况\">标题</h3></a>";
        var found=BilibiliDiscovery.parseHtml(html+html+"<a href=\"https://evil.test/video/BV123\"><h3 title=\"坏链接\">x</h3></a>");
        assertEquals(1,found.size());assertEquals(196,found.getFirst().duration());assertEquals("根的判别式 & 根的情况",found.getFirst().title());assertEquals("https://www.bilibili.com/video/BV18x411x79x/",found.getFirst().url());
    }
    @Test void captchaAndUnlinkedMentionsAreNotSearchResults(){
        assertTrue(BilibiliDiscovery.parseHtml("<p>请完成验证</p> BV18x411x79x <h3 title=\"伪造标题\">x</h3>").isEmpty());
    }
}
