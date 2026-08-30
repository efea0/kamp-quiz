package quiz.web;

import com.sun.net.httpserver.HttpServer;
import quiz.core.QuizSet;
import quiz.core.Scoreboard;
import quiz.model.Question;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * Quiz'i yerel agda yayinlayan web sunucusu.
 *
 * Java'nin icinde hazir gelen HttpServer kullanilir; hicbir dis kutuphane yok.
 * Ayni Wi-Fi'daki telefonlar tarayiciyla baglanip quize katilabilir.
 *
 * Bu sinif SADECE sunucuyu kurar: paylasilan durumu {@link ServerContext}
 * icinde toplar, her sayfa grubu icin bir sayfa nesnesi olusturur ve
 * URL'leri o nesnelerin metotlarina baglar. Sayfalarin kendisi (HTML uretmek,
 * formu okumak, yonlendirmek) `pages/` altindaki siniflardadir — o siniflar
 * "hangi sayfa ne yapiyor" sorusunun cevabini tasir, bu sinif sadece
 * "hangi URL hangi sayfaya gidiyor" sorusunu.
 */
public class WebServer {

    private final ServerContext ctx;
    private volatile HttpServer server;
    private volatile ExecutorService executor;

    public WebServer(List<Question> allQuestions, List<QuizSet> sets,
                     Path questionsDir, Path setsDir, Scoreboard scoreboard, int port) {
        this.ctx = new ServerContext(allQuestions, sets, questionsDir, setsDir, scoreboard, port);
    }

    public void start() throws IOException {
        start(true);
    }

    /** Smoke testi gibi programatik kullanimlarda banner yazmadan sunucuyu acar. */
    public void startQuietly() throws IOException {
        start(false);
    }

    private void start(boolean announce) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(ctx.getPort()), 0);

        HomePages homePages = new HomePages(ctx);
        QuizPages quizPages = new QuizPages(ctx);
        RoomPages roomPages = new RoomPages(ctx);
        ExportPages exportPages = new ExportPages(ctx);
        BoardPage boardPage = new BoardPage(ctx);
        GeneratePages generatePages = new GeneratePages(ctx);

        // ---- rota tablosu: hangi URL hangi sayfaya gidiyor ----
        httpServer.createContext("/", homePages::handleHome);
        httpServer.createContext("/start", homePages::handleStart);
        httpServer.createContext("/ayarla", homePages::handleCustom);
        httpServer.createContext("/katil", homePages::handleJoin);

        httpServer.createContext("/quiz", quizPages::handleQuiz);
        httpServer.createContext("/cevap", quizPages::handleAnswer);
        httpServer.createContext("/devam", quizPages::handleContinue);
        httpServer.createContext("/sonuc", quizPages::handleResult);
        httpServer.createContext("/tekrar", quizPages::handleRetry);

        httpServer.createContext("/kur", roomPages::handleHostSetup);
        httpServer.createContext("/oda", roomPages::handleHostPanel);
        httpServer.createContext("/ekran", roomPages::handleScreen);
        httpServer.createContext("/rapor", roomPages::handleReport);
        httpServer.createContext("/disaktar/oda", exportPages::handleRoomCsv);
        httpServer.createContext("/disaktar/sorular", exportPages::handleQuestionsCsv);

        httpServer.createContext("/tablo", boardPage::handleBoard);
        httpServer.createContext("/uret", generatePages::handleGenerate);

        httpServer.createContext("/style.css", exchange ->
                ctx.send(exchange, 200, "text/css; charset=UTF-8", Html.CSS));

        // Tek is parcacigi olsaydi bir kisi sayfayi beklerken digerleri kilitlenirdi.
        ExecutorService threadPool = Executors.newFixedThreadPool(16);
        httpServer.setExecutor(threadPool);
        this.server = httpServer;
        this.executor = threadPool;
        ctx.setBoundPort(httpServer.getAddress().getPort());
        httpServer.start();

        if (announce) {
            printAddresses();
            printAiStatus();
        }
    }

    /** Test sunucusunu ve ona ait is parcaciklarini kapatir. */
    public void stop() {
        HttpServer running = server;
        if (running != null) {
            running.stop(0);
            server = null;
        }
        ExecutorService threadPool = executor;
        if (threadPool != null) {
            threadPool.shutdownNow();
            executor = null;
        }
    }

    /** Sunucunun gercekten dinledigi portu verir; 0 ile baslatilan testlerde de calisir. */
    public int getBoundPort() {
        return ctx.getPort();
    }

    /** Baglanti adreslerini ekrana basar; katilimcilar bunu telefona yazacak. */
    private void printAddresses() {
        System.out.println();
        System.out.println("=========================================");
        System.out.println("  SUNUCU ÇALIŞIYOR");
        System.out.println("=========================================");
        System.out.println("  Bu bilgisayarda : http://localhost:" + ctx.getPort());

        for (String address : ServerContext.localAddresses()) {
            System.out.println("  Aynı Wi-Fi'dan  : http://" + address + ":" + ctx.getPort());
        }

        System.out.println();
        System.out.println("  Katılımcılar yukarıdaki adresi tarayıcıya yazsın.");
        System.out.println("  Durdurmak için: Ctrl+C");
        System.out.println("=========================================");
    }

    /** Uretim ozelliginin durumunu ve varsa uyarilari basar. */
    private void printAiStatus() {
        var generator = ctx.getGenerator();
        if (generator.isEnabled()) {
            System.out.println("  Soru üretici : " + generator.describe());
            if (ctx.getAdminKey().isEmpty()) {
                System.out.println("  UYARI        : /uret parolasız. QUIZ_ADMIN_KEY tanımla.");
            }
        }
        for (String warning : generator.getWarnings()) {
            System.out.println("  UYARI        : " + warning);
        }
        if (generator.isEnabled() || !generator.getWarnings().isEmpty()) {
            System.out.println("=========================================");
        }
    }
}
