package edu.demo.mathvideo;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

/**
 * 本地一键启动器：在 IDE 中直接运行本类的 main 方法，
 * 自动拉起 Ollama 视觉模型服务与本应用并打开浏览器，
 * 等效于双击 启动系统.cmd / 启动服务.ps1。
 */
public final class Bootstrap {

    private static final String APP_PORT = "8086";
    private static final String APP_URL = "http://localhost:" + APP_PORT + "/";
    private static final String HEALTH_URL = "http://127.0.0.1:" + APP_PORT + "/api/v2/health";
    private static final String OLLAMA_TAGS_URL = "http://127.0.0.1:11434/api/tags";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();

    public static void main(String[] args) throws Exception {
        Path root = detectRoot();
        Path jar = root.resolve("target/math-video-ai-1.0.0.jar");
        Path ollama = root.resolve("tools/ollama/ollama.exe");
        require(Files.exists(jar), "未找到构建产物 " + jar + "，请先执行 mvn clean package。");
        require(Files.exists(ollama), "未找到 Ollama " + ollama);
        System.out.println("项目目录: " + root);

        if (httpOk(OLLAMA_TAGS_URL)) {
            System.out.println("Ollama 已在运行");
        } else {
            System.out.println("启动 Ollama 视觉模型服务...");
            startOllama(root, ollama);
        }

        if (appHealthy()) {
            System.out.println("Java 应用已在运行");
        } else {
            System.out.println("启动 Java 应用 (端口 " + APP_PORT + ")...");
            startApp(root, jar);
        }

        System.out.println("\n启动完成: " + APP_URL);
        openBrowser(APP_URL);
    }

    /** 优先使用工作目录；若其中没有构建产物，则从类路径向上查找项目根目录。 */
    private static Path detectRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve("target/math-video-ai-1.0.0.jar"))) return cwd;
        try {
            Path here = Path.of(Bootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath().normalize();
            for (Path p = here; p != null; p = p.getParent()) {
                if (Files.exists(p.resolve("target/math-video-ai-1.0.0.jar"))) return p;
            }
        } catch (Exception ignored) {
            // 兜底失败时退回工作目录
        }
        return cwd;
    }

    private static void startOllama(Path root, Path ollama) throws Exception {
        Files.createDirectories(root.resolve("data"));
        ProcessBuilder pb = new ProcessBuilder(ollama.toString(), "serve");
        pb.directory(root.toFile());
        pb.environment().put("OLLAMA_MODELS", root.resolve("models").toString());
        pb.environment().put("OLLAMA_HOST", "127.0.0.1:11434");
        pb.environment().put("OLLAMA_NO_CLOUD", "1");
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(root.resolve("data/ollama-out.log").toFile()));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(root.resolve("data/ollama-error.log").toFile()));
        pb.start();
        await("Ollama", OLLAMA_TAGS_URL, 60);
    }

    private static void startApp(Path root, Path jar) throws Exception {
        Files.createDirectories(root.resolve("data"));
        String exe = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", exe);
        ProcessBuilder pb = new ProcessBuilder(java.toString(),
                "-Dfile.encoding=UTF-8", "-jar", jar.toString(), "--server.port=" + APP_PORT);
        pb.directory(root.toFile());
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(root.resolve("data/app-out.log").toFile()));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(root.resolve("data/app-error.log").toFile()));
        pb.start();
        await("Java 应用", HEALTH_URL, 90);
    }

    private static void await(String name, String url, int seconds) throws Exception {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (httpOk(url)) {
                System.out.println(name + " 已就绪");
                return;
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException(name + " 启动超时，请查看 data/ 目录下的日志。");
    }

    private static boolean appHealthy() {
        return httpOk(HEALTH_URL);
    }

    private static boolean httpOk(String url) {
        try {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
            return r.statusCode() / 100 == 2;
        } catch (Exception e) {
            return false;
        }
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {
            // 继续尝试系统级方式
        }
        try {
            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
        } catch (IOException e) {
            System.out.println("请手动打开浏览器访问: " + url);
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }

    private Bootstrap() {
    }
}
