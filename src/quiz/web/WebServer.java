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

    public WebServer(List<Question> allQuestions, List<QuizSet> sets,
                     Path questionsDir, Path setsDir, Scoreboard scoreboard, int port) {
        this.ctx = new ServerContext(allQuestions, sets, questionsDir, setsDir, scoreboard, port);
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(ctx.getPort()), 0);

        HomePages homePages = new HomePages(ctx);
        QuizPages quizPages = new QuizPages(ctx);
        RoomPages roomPages = new RoomPages(ctx);
        BoardPage boardPage = new BoardPage(ctx);
        GeneratePages generatePages = new GeneratePages(ctx);
        ExportPages exportPages = new ExportPages(ctx);

        // ---- rota tablosu: hangi URL hangi sayfaya gidiyor ----
        server.createContext("/", homePages::handleHome);
        server.createContext("/start", homePages::handleStart);
        server.createContext("/ayarla", homePages::handleCustom);
        server.createContext("/katil", homePages::handleJoin);

        server.createContext("/quiz", quizPages::handleQuiz);
        server.createContext("/cevap", quizPages::handleAnswer);
        server.createContext("/devam", quizPages::handleContinue);
        server.createContext("/sonuc", quizPages::handleResult);
        server.createContext("/tekrar", quizPages::handleRetry);

        server.createContext("/kur", roomPages::handleHostSetup);
        server.createContext("/oda", roomPages::handleHostPanel);
        server.createContext("/ekran", roomPages::handleScreen);
        server.createContext("/rapor", roomPages::handleReport);

        server.createContext("/disaktar/oda", exportPages::handleRoomCsv);
        server.createContext("/disaktar/sorular", exportPages::handleQuestionsCsv);

        server.createContext("/tablo", boardPage::handleBoard);
        server.createContext("/uret", generatePages::handleGenerate);

        server.createContext("/style.css", exchange ->
                ctx.send(exchange, 200, "text/css; charset=UTF-8", Html.CSS));

        // Tek is parcacigi olsaydi bir kisi sayfayi beklerken digerleri kilitlenirdi.
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();

        printAddresses();
        printAiStatus();
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
