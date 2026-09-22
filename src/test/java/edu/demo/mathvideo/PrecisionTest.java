package edu.demo.mathvideo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import edu.demo.mathvideo.PrecisionModels.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.net.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class PrecisionTest {
    @TempDir Path temp;
    final ObjectMapper mapper=new ObjectMapper();
    final SkillCatalog catalog=new SkillCatalog();
    PrecisionStore store;PrecisionMedia media;ModelGateway model;PrecisionService service;
    @BeforeEach void setup()throws Exception {
        LibraryStore legacy=new LibraryStore(mapper,temp.toString());legacy.initialize();
        store=new PrecisionStore(mapper,legacy);store.init();
        media=new PrecisionMedia(store,catalog,mapper,"E:/自动识别视频ai/tools");
        model=org.mockito.Mockito.spy(new ModelGateway(mapper,store,""));model.init();service=new PrecisionService(store,catalog,model,mapper,media);
    }
    @Test void parsesRealTimedSubtitlesAndRejectsInvalidRanges() throws Exception {
        var cues=SubtitleParser.parse("1\n00:00:01,250 --> 00:00:07,900\n判别式小于零\n\n2\n00:00:08,000 --> 00:00:15,000\n没有实数根\n",mapper);
        assertEquals(2,cues.size());assertEquals(1.25,cues.getFirst().start());assertEquals(15,cues.getLast().end());
        assertEquals("根的情况",SubtitleParser.parse("{\"body\":[{\"from\":1,\"to\":8,\"content\":\"根的情况\"}]}",mapper).getFirst().text());
        assertThrows(Exception.class,()->SubtitleParser.parse("[{\"startSeconds\":9,\"endSeconds\":2,\"text\":\"bad\"}]",mapper));
    }
    @Test void bilibiliImportOnlyAcceptsCanonicalPublicVideoUrls() {
        assertEquals("https://www.bilibili.com/video/BV18x411x79x/?p=2",PrecisionMedia.bilibiliUrl("https://www.bilibili.com/video/BV18x411x79x/?p=2&tracking=123"));
        for(String bad:List.of("http://127.0.0.1/x","https://bilibili.com.evil.test/video/BV123","file:///C:/secret","https://user:pass@www.bilibili.com/video/BV123","https://www.bilibili.com:443/video/BV123"))
            assertThrows(IllegalArgumentException.class,()->PrecisionMedia.bilibiliUrl(bad));
    }
    @Test void differentDiscriminantConditionsMustNotMatch() {
        String negative="Δ＜0 时为什么没有实数根";
        assertFalse(LearningScope.compatible("正确计算Δ=0，但误写有两个不相等实数根","",negative));
        assertFalse(LearningScope.compatible("正确计算Δ=8","",negative));
        assertTrue(LearningScope.compatible("Δ=-8时应无实数根","",negative));
        assertFalse(LearningScope.compatible("学生Δ=-8；Δ<0无根，Δ=0有相等根，Δ>0有不等根","Δ = (-2)² - 12 = -8","Δ=0有两个相等根"));
        assertTrue(LearningScope.compatible("Δ=0","","Δ>0有两个不等根，Δ=0有两个相等根，Δ<0无实数根"));
    }
    @Test void actualClipIsIndependentAndMatchesOnlyConfirmedSkill() throws Exception {
        Path video=temp.resolve("source.mp4");
        media.run(List.of(media.tool("ffmpeg"),"-hide_banner","-y","-f","lavfi","-i","color=c=red:s=320x240:r=25:d=6","-f","lavfi","-i","color=c=blue:s=320x240:r=25:d=6","-filter_complex","[0:v][1:v]concat=n=2:v=1:a=0[v]","-map","[v]","-c:v","libx264","-pix_fmt","yuv420p",video.toString()),30);
        Asset a=media.upload(new MockMultipartFile("file","source.mp4","video/mp4",Files.readAllBytes(video)),"工程测试，不进入真实资料库","","",9);
        assertThrows(IllegalArgumentException.class,()->media.publish(a.id(),new Mark("错误","discriminant-count",6,12,"未审核",false)));
        assertThrows(IllegalArgumentException.class,()->media.publish(a.id(),new Mark("错误","discriminant-count",11,20,"越界",true)));
        Clip c=media.publish(a.id(),new Mark("判断根的个数","discriminant-count",6,12,"只讲判别式符号与根的个数",true));
        assertTrue(Files.exists(media.clipFile(c)));assertNotEquals(media.source(a),media.clipFile(c));assertEquals(6,media.duration(media.clipFile(c)),.6);
        Path frame=temp.resolve("first.png");media.run(List.of(media.tool("ffmpeg"),"-y","-i",media.clipFile(c).toString(),"-frames:v","1",frame.toString()),15);
        Color color=new Color(ImageIO.read(frame.toFile()).getRGB(100,100));assertTrue(color.getBlue()>200&&color.getRed()<30,"实际首帧应是第6秒以后的蓝色，不能仍播放原片开头");
        store.putDiagnosis(new Diagnosis("d","题目","作答","依据",true,true,9,List.of("discriminant-count","discriminant-compute"),"", "now"));
        org.mockito.Mockito.doReturn(mapper.readTree("{\"clipIds\":[\"invented-id\",\""+c.id()+"\",\""+c.id()+"\"]}")).when(model).complete(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.isNull());
        var lessons=service.recommend("d","discriminant-count");assertEquals(1,lessons.size());assertTrue(lessons.getFirst().playUrl().contains("/clips/"));
        assertTrue(service.recommend("d","discriminant-compute").isEmpty(),"不匹配时不能退回整节课");
        assertThrows(IllegalArgumentException.class,()->service.recommend("d","linear-system"));
        service.feedback("d",c.id(),"understood");assertEquals(1,store.feedback().size());
        PrecisionStore reloaded=new PrecisionStore(mapper,new LibraryStore(mapper,temp.toString()));reloaded.init();assertEquals(c.id(),reloaded.clips().getFirst().id());
    }
    @Test void photosAreSentToVisionModelAndUnknownSkillIdsAreDiscarded()throws Exception {
        HttpServer stub=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        stub.createContext("/api/chat",exchange->{
            var request=mapper.readTree(exchange.getRequestBody());
            assertFalse(request.path("messages").get(1).path("images").get(0).asText().isBlank());
            assertEquals("json",request.path("format").asText());
            byte[] response=mapper.writeValueAsBytes(java.util.Map.of("message",java.util.Map.of("content","{\"readable\":true,\"juniorMath\":true,\"question\":\"x²-2x+3=0\",\"studentWork\":\"未看到作答痕迹\",\"evidence\":\"不能仅凭题目确定卡点\",\"skillIds\":[\"discriminant-count\",\"fake-id\",\"discriminant-count\"],\"clarification\":\"你卡在哪一步？\"}")));
            exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });stub.start();
        try {
            model.configure(new ModelGateway.Settings("ollama","http://127.0.0.1:"+stub.getAddress().getPort(),"test-vision"),"");
            Diagnosis d=service.analyze(fixture(),9,"");assertEquals(List.of("discriminant-count"),d.skillIds());assertTrue(Files.exists(store.root().resolve("photos").resolve(d.id()+".jpg")));assertTrue(service.recommend(d.id(),"discriminant-count").isEmpty());
        }finally{stub.stop(0);}
    }
    @Test void rejectsInvalidPhotosAndUnreadableDiagnoses()throws Exception {
        assertThrows(IllegalArgumentException.class,()->PrecisionService.preparePhoto("not an image".getBytes()));
        store.putDiagnosis(new Diagnosis("bad","","","",false,true,9,List.of("discriminant-count"),"请重拍","now"));
        assertThrows(IllegalArgumentException.class,()->service.recommend("bad","discriminant-count"));
    }
    @Test void acceptsOnlyPureJsonFromEmptyContentCompatibilityResponse()throws Exception {
        AtomicReference<String> alternate=new AtomicReference<>("{\"readable\":true,\"skillIds\":[\"discriminant-count\"]}");
        HttpServer stub=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        stub.createContext("/api/chat",exchange->{
            try {
                exchange.getRequestBody().readAllBytes();
                byte[] response=mapper.writeValueAsBytes(java.util.Map.of("message",java.util.Map.of("content","","thinking",alternate.get())));
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(200,response.length);
                exchange.getResponseBody().write(response);
            } finally { exchange.close(); }
        });
        stub.start();
        try {
            model.configure(new ModelGateway.Settings("ollama","http://127.0.0.1:"+stub.getAddress().getPort(),"test-vision"),"");
            var result=model.complete("兼容响应回归测试",null);
            assertTrue(result.path("readable").asBoolean());
            assertEquals("discriminant-count",result.path("skillIds").get(0).asText());
            for(String invalid:List.of("","需要先检查题目，再给出答案。","分析如下：{\"readable\":true}","{不是有效的 JSON}")) {
                alternate.set(invalid);
                assertThrows(IllegalStateException.class,()->model.complete("拒绝空内容和自由推理文本",null));
            }
        } finally { stub.stop(0); }
    }
    @Test void interactiveJobsFinishWhileBothBackgroundWorkersAreBlocked()throws Exception {
        WorkJobs jobs=new WorkJobs();
        CountDownLatch backgroundStarted=new CountDownLatch(2),releaseBackground=new CountDownLatch(1);
        try {
            for(int i=0;i<2;i++)jobs.submit("阻塞的后台媒体任务",()->{
                backgroundStarted.countDown();
                releaseBackground.await();
                return "background-finished";
            });
            assertTrue(backgroundStarted.await(5,TimeUnit.SECONDS),"两个后台线程都应进入阻塞任务");
            String id=jobs.submitInteractive("照片识别",()->"interactive-finished");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(!List.of("done","failed").contains(jobs.get(id).state())&&System.nanoTime()<deadline)
                TimeUnit.MILLISECONDS.sleep(10);
            assertEquals("done",jobs.get(id).state(),"后台媒体任务不能阻塞孩子的照片识别");
            assertEquals("interactive-finished",jobs.get(id).result());
            assertEquals(1L,releaseBackground.getCount(),"交互任务完成时后台任务应仍处于阻塞状态");
        } finally {
            releaseBackground.countDown();
            jobs.close();
        }
    }
    @Test void producesPhotoFixtureForLocalVisionSmokeTest()throws Exception {
        Path dir=Path.of("target/verification");Files.createDirectories(dir);Files.write(dir.resolve("math-work.jpg"),fixture());
    }
    static byte[] fixture()throws Exception {
        BufferedImage image=new BufferedImage(1300,620,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        g.setColor(Color.WHITE);g.fillRect(0,0,1300,620);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,34));g.setColor(Color.BLACK);
        g.drawString("初三数学：不解方程，判断实数根的情况。",55,80);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,46));g.drawString("x² - 2x + 3 = 0",55,160);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,32));g.drawString("我的解答：a = 1，b = -2，c = 3",55,250);
        g.drawString("Δ = (-2)² - 4 × 1 × 3 = 4 - 12 = -8",55,330);
        g.setColor(new Color(30,60,150));g.drawString("因为 Δ < 0，所以有两个不相等的实数根。",55,415);
        g.setColor(Color.GRAY);g.drawString("我不确定最后一步对不对。",55,520);g.dispose();
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"jpg",out);return out.toByteArray();
    }
}
