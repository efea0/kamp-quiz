package quiz.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import quiz.core.QuestionBank;
import quiz.core.Quiz;
import quiz.core.Scoreboard;
import quiz.model.Question;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Quiz'i yerel agda yayinlayan web sunucusu.
 *
 * Java'nin icinde hazir gelen HttpServer kullanilir; hicbir dis kutuphane yok.
 * Ayni Wi-Fi'daki telefonlar tarayiciyla baglanip quize katilabilir.
 */
public class WebServer {

    private static final String COOKIE_NAME = "qsid";

    private final List<Question> allQuestions;
    private final Scoreboard scoreboard;
    private final int port;

    /** Oyuncu oturumlari. Ayni anda birden fazla istek geldigi icin es zamanli harita. */
    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    public WebServer(List<Question> allQuestions, Scoreboard scoreboard, int port) {
        this.allQuestions = allQuestions;
        this.scoreboard = scoreboard;
        this.port = port;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", this::handleHome);
        server.createContext("/start", this::handleStart);
        server.createContext("/quiz", this::handleQuiz);
        server.createContext("/cevap", this::handleAnswer);
        server.createContext("/sonuc", this::handleResult);
        server.createContext("/tablo", this::handleBoard);
        server.createContext("/style.css", this::handleCss);

        // Tek is parcacigi olsaydi bir kisi sayfayi beklerken digerleri kilitlenirdi.
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();

        printAddresses();
    }

    // ---------------------------------------------------------------- sayfalar

    /** Giris sayfasi: isim, kategori ve soru sayisi formu. */
    private void handleHome(HttpExchange exchange) throws IOException {
        if (!"/".equals(exchange.getRequestURI().getPath())) {
            sendHtml(exchange, 404, Html.page("Bulunamadı",
                    "    <div class=\"card\"><h1>404</h1><p class=\"muted\">Böyle bir sayfa yok.</p>"
                    + "<p><a href=\"/\">Başa dön</a></p></div>"));
            return;
        }

        List<String> categories = QuestionBank.categoriesOf(allQuestions);

        StringBuilder options = new StringBuilder();
        options.append("        <option value=\"\">Hepsi karışık (")
               .append(allQuestions.size()).append(" soru)</option>\n");
        for (String category : categories) {
            int count = QuestionBank.byCategory(allQuestions, category).size();
            options.append("        <option value=\"").append(Html.escape(category)).append("\">")
                   .append(Html.escape(category)).append(" (").append(count).append(")</option>\n");
        }

        String body = """
                    <div class="card">
                      <h1>Kamp Quiz</h1>
                      <p class="muted">%d soru, %d kategori. Adını yaz ve başla.</p>
                      <form method="POST" action="/start">
                        <label class="field" for="isim">Adın</label>
                        <input type="text" id="isim" name="isim" maxlength="20" required autocomplete="off">

                        <label class="field" for="kategori">Kategori</label>
                        <select id="kategori" name="kategori">
                %s        </select>

                        <label class="field" for="adet">Soru sayısı</label>
                        <select id="adet" name="adet">
                          <option value="5">5 soru</option>
                          <option value="10" selected>10 soru</option>
                          <option value="20">20 soru</option>
                        </select>

                        <label class="field" for="sure">Soru başına süre</label>
                        <select id="sure" name="sure">
                          <option value="10">10 saniye - hızlı tur</option>
                          <option value="20" selected>20 saniye - normal</option>
                          <option value="45">45 saniye - rahat</option>
                        </select>

                        <button type="submit">Başla</button>
                      </form>
                    </div>
                    <p class="center"><a href="/tablo">Lider tablosunu gör</a></p>
                """.formatted(allQuestions.size(), categories.size(), options);

        sendHtml(exchange, 200, Html.page("Kamp Quiz", body));
    }

    /** Formu isler, oturum acar ve quize yonlendirir. */
    private void handleStart(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            redirect(exchange, "/");
            return;
        }

        Map<String, String> form = readForm(exchange);
        String name = form.getOrDefault("isim", "").trim();
        if (name.isEmpty()) {
            name = "Misafir";
        }
        if (name.length() > 20) {
            name = name.substring(0, 20);
        }

        String category = form.getOrDefault("kategori", "");
        List<Question> pool = category.isEmpty()
                ? allQuestions
                : QuestionBank.byCategory(allQuestions, category);
        if (pool.isEmpty()) {
            pool = allQuestions;
        }

        int count = parseIntOr(form.get("adet"), 10);
        int seconds = parseIntOr(form.get("sure"), 20);

        Quiz quiz = new Quiz(pool);
        quiz.shuffle();
        quiz.limitTo(count);
        quiz.setTimeLimitSeconds(seconds);

        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new GameSession(name, quiz));

        exchange.getResponseHeaders().add("Set-Cookie",
                COOKIE_NAME + "=" + sessionId + "; Path=/; Max-Age=7200; SameSite=Lax");
        redirect(exchange, "/quiz");
    }

    /** Sirdaki soruyu gosterir. */
    private void handleQuiz(HttpExchange exchange) throws IOException {
        GameSession session = currentSession(exchange);
        if (session == null) {
            redirect(exchange, "/");
            return;
        }

        Quiz quiz = session.getQuiz();
        if (!quiz.hasNext()) {
            redirect(exchange, "/sonuc");
            return;
        }

        Question question = quiz.currentQuestion();
        String[] options = question.getOptions();

        StringBuilder radios = new StringBuilder();
        for (int i = 0; i < options.length; i++) {
            radios.append("        <label class=\"option\">")
                  .append("<input type=\"radio\" name=\"cevap\" value=\"").append(i).append("\" required>")
                  .append("<span>").append(Html.escape(options[i])).append("</span></label>\n");
        }

        int percent = Math.round((quiz.getQuestionNumber() - 1) * 100f / quiz.getTotal());
        int limit = quiz.getTimeLimitSeconds();

        // Sayac soru ekrana gelince baslar.
        quiz.startQuestionTimer();

        String body = """
                %s    <div class="progress"><div style="width:%d%%"></div></div>
                    <div class="card">
                      <span class="tag">%s</span>
                      <div class="timer">
                        <span class="muted">Soru %d / %d</span>
                        <span><b id="kalan">%d</b> <span class="muted">sn</span></span>
                      </div>
                      <div class="timebar" id="cubuk"><div id="dolgu" style="width:100%%"></div></div>
                      <h2>%s</h2>
                      <form method="POST" action="/cevap" id="cevapForm">
                %s          <button type="submit">Cevapla</button>
                      </form>
                    </div>
                    <script>
                      (function () {
                        var toplam = %d, kalan = toplam;
                        var sayi = document.getElementById('kalan');
                        var dolgu = document.getElementById('dolgu');
                        var cubuk = document.getElementById('cubuk');
                        var form = document.getElementById('cevapForm');
                        var sayac = setInterval(function () {
                          kalan--;
                          sayi.textContent = kalan < 0 ? 0 : kalan;
                          dolgu.style.width = Math.max(0, kalan / toplam * 100) + '%%';
                          if (kalan <= 5) { cubuk.classList.add('hurry'); }
                          if (kalan <= 0) {
                            clearInterval(sayac);
                            form.submit();   // sure doldu, bos gonder
                          }
                        }, 1000);
                      })();
                    </script>
                """.formatted(
                session.consumeFeedback(),
                percent,
                Html.escape(question.getCategory()),
                quiz.getQuestionNumber(),
                quiz.getTotal(),
                limit,
                Html.escape(question.getText()),
                radios,
                limit);

        sendHtml(exchange, 200, Html.page("Soru " + quiz.getQuestionNumber(), body));
    }

    /** Cevabi degerlendirir ve bir sonraki soruya yonlendirir. */
    private void handleAnswer(HttpExchange exchange) throws IOException {
        GameSession session = currentSession(exchange);
        if (session == null) {
            redirect(exchange, "/");
            return;
        }

        Quiz quiz = session.getQuiz();
        if (!"POST".equals(exchange.getRequestMethod()) || !quiz.hasNext()) {
            redirect(exchange, "/quiz");
            return;
        }

        Question question = quiz.currentQuestion();
        int answer = parseIntOr(readForm(exchange).get("cevap"), -1);
        Quiz.AnswerResult result = quiz.submitAnswer(answer);

        String message;
        if (result.timedOut()) {
            message = "Süre doldu — doğru cevap: " + question.getCorrectOption();
        } else if (result.correct()) {
            message = "Doğru!  +" + result.earnedPoints() + " puan";
        } else {
            message = "Yanlış — doğru cevap: " + question.getCorrectOption();
        }
        session.setFeedback(result.correct(), message, question.getExplanation());

        // Cevaptan sonra yonlendiriyoruz ki kullanici sayfayi yenileyince
        // ayni cevap tekrar gonderilmesin (POST-Redirect-GET deseni).
        redirect(exchange, "/quiz");
    }

    /** Sonuc sayfasi; skoru bir kez kaydeder. */
    private void handleResult(HttpExchange exchange) throws IOException {
        GameSession session = currentSession(exchange);
        if (session == null) {
            redirect(exchange, "/");
            return;
        }

        Quiz quiz = session.getQuiz();
        if (!session.isScoreSaved()) {
            try {
                scoreboard.save(session.getPlayerName(), quiz.getScore(), quiz.getTotal(), quiz.getPoints());
                session.markScoreSaved();
            } catch (IOException e) {
                System.out.println("Skor kaydedilemedi: " + e.getMessage());
            }
        }

        String body = """
                    <div class="card center">
                      <p class="muted">%s</p>
                      <div class="score">%d <span class="muted" style="font-size:1rem">puan</span></div>
                      <p class="muted">%d / %d doğru &middot; %%%d</p>
                    </div>
                    <div class="card center">
                      <p><a href="/tablo">Lider tablosu</a></p>
                      <p><a href="/">Yeniden oyna</a></p>
                    </div>
                """.formatted(
                Html.escape(session.getPlayerName()),
                quiz.getPoints(), quiz.getScore(), quiz.getTotal(), quiz.getPercentage());

        sendHtml(exchange, 200, Html.page("Sonuç", body));
    }

    /** Lider tablosu. */
    private void handleBoard(HttpExchange exchange) throws IOException {
        List<Scoreboard.Entry> entries;
        try {
            entries = scoreboard.topScores(20);
        } catch (IOException e) {
            entries = List.of();
        }

        StringBuilder rows = new StringBuilder();
        if (entries.isEmpty()) {
            rows.append("        <tr><td colspan=\"4\" class=\"muted\">Henüz kayıt yok.</td></tr>\n");
        } else {
            int rank = 1;
            for (Scoreboard.Entry e : entries) {
                rows.append("        <tr><td>").append(rank++).append("</td><td>")
                    .append(Html.escape(e.name())).append("</td><td class=\"num points\">")
                    .append(e.points()).append("</td><td class=\"num\">")
                    .append(e.score()).append("/").append(e.total())
                    .append("</td></tr>\n");
            }
        }

        String body = """
                    <div class="card">
                      <h1>Lider Tablosu</h1>
                      <table>
                        <tr><th>#</th><th>Oyuncu</th><th class="num">Puan</th><th class="num">Doğru</th></tr>
                %s      </table>
                    </div>
                    <p class="center"><a href="/">Ana sayfa</a></p>
                """.formatted(rows);

        sendHtml(exchange, 200, Html.page("Lider Tablosu", body));
    }

    private void handleCss(HttpExchange exchange) throws IOException {
        send(exchange, 200, "text/css; charset=UTF-8", Html.CSS);
    }

    // ------------------------------------------------------------- yardimcilar

    /** Tarayicidan gelen cerezle oturumu bulur. */
    private GameSession currentSession(HttpExchange exchange) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) {
            return null;
        }
        for (String header : cookies) {
            for (String cookie : header.split(";")) {
                String[] pair = cookie.trim().split("=", 2);
                if (pair.length == 2 && COOKIE_NAME.equals(pair[0])) {
                    return sessions.get(pair[1]);
                }
            }
        }
        return null;
    }

    /** POST govdesindeki 'a=1&b=2' bicimini haritaya cevirir. */
    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> form = new HashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            form.put(key, value);
        }
        return form;
    }

    private static int parseIntOr(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(303, -1);   // 303 = "gordum, simdi suraya git"
        exchange.close();
    }

    private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        send(exchange, status, "text/html; charset=UTF-8", html);
    }

    private void send(HttpExchange exchange, int status, String contentType, String content)
            throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Baglanti adreslerini ekrana basar; katilimcilar bunu telefona yazacak. */
    private void printAddresses() {
        System.out.println();
        System.out.println("=========================================");
        System.out.println("  SUNUCU ÇALIŞIYOR");
        System.out.println("=========================================");
        System.out.println("  Bu bilgisayarda : http://localhost:" + port);

        for (String address : localAddresses()) {
            System.out.println("  Aynı Wi-Fi'dan  : http://" + address + ":" + port);
        }

        System.out.println();
        System.out.println("  Katılımcılar yukarıdaki adresi tarayıcıya yazsın.");
        System.out.println("  Durdurmak için: Ctrl+C");
        System.out.println("=========================================");
    }

    /** Bilgisayarin yerel agdaki IPv4 adreslerini bulur. */
    private static List<String> localAddresses() {
        List<String> found = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    String ip = address.getHostAddress();
                    if (address.isSiteLocalAddress() && !ip.contains(":")) {
                        found.add(ip);
                    }
                }
            }
        } catch (Exception e) {
            // Ag bilgisi okunamadi; sadece localhost gosterilir.
        }
        return found;
    }
}
