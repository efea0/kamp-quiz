package quiz.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import quiz.core.QuestionBank;
import quiz.core.Quiz;
import quiz.core.QuizSet;
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
    private final List<QuizSet> sets;
    private final Scoreboard scoreboard;
    private final int port;

    /** Oyuncu oturumlari. Ayni anda birden fazla istek geldigi icin es zamanli harita. */
    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    public WebServer(List<Question> allQuestions, List<QuizSet> sets,
                     Scoreboard scoreboard, int port) {
        this.allQuestions = allQuestions;
        this.sets = sets;
        this.scoreboard = scoreboard;
        this.port = port;
    }

    /** Adiyla bir hazir seti bulur. */
    private QuizSet findSet(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (QuizSet set : sets) {
            if (set.getName().equals(name)) {
                return set;
            }
        }
        return null;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", this::handleHome);
        server.createContext("/start", this::handleStart);
        server.createContext("/ayarla", this::handleCustom);
        server.createContext("/quiz", this::handleQuiz);
        server.createContext("/cevap", this::handleAnswer);
        server.createContext("/devam", this::handleContinue);
        server.createContext("/sonuc", this::handleResult);
        server.createContext("/tablo", this::handleBoard);
        server.createContext("/style.css", this::handleCss);

        // Tek is parcacigi olsaydi bir kisi sayfayi beklerken digerleri kilitlenirdi.
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();

        printAddresses();
    }

    // ---------------------------------------------------------------- sayfalar

    /** Giris sayfasi: isim + hazir test kartlari. */
    private void handleHome(HttpExchange exchange) throws IOException {
        if (!"/".equals(exchange.getRequestURI().getPath())) {
            sendHtml(exchange, 404, Html.page("Bulunamadı", """
                    <div class="screen">
                      <div class="card center">
                        <h1>404</h1>
                        <p class="muted">Böyle bir sayfa yok.</p>
                      </div>
                      <div class="actions"><a class="btn" href="/">Başa dön</a></div>
                    </div>
                    """));
            return;
        }

        StringBuilder cards = new StringBuilder();
        for (QuizSet set : sets) {
            cards.append("      <button class=\"setcard\" type=\"submit\" name=\"set\" value=\"")
                 .append(Html.escape(set.getName())).append("\">")
                 .append("<b>").append(Html.escape(set.getName())).append("</b>")
                 .append("<small>").append(Html.escape(set.getDescription())).append("</small>")
                 .append("<em>").append(set.totalQuestions()).append(" soru · ")
                 .append(set.getTimeLimitSeconds()).append(" sn</em>")
                 .append("</button>\n");
        }
        if (sets.isEmpty()) {
            cards.append("      <p class=\"muted small\">Hazır test bulunamadı. sets/ klasörüne bir .txt ekleyebilirsin.</p>\n");
        }

        String body = """
                <div class="screen">
                  <p class="eyebrow">Kamp Quiz</p>
                  <h1>Hangi testi çözelim?</h1>
                  <p class="muted small">%d soruluk havuz · hızlı cevap daha çok puan getirir</p>

                  <form method="POST" action="/start" style="margin-top:22px">
                    <label class="field" for="isim">Adın</label>
                    <input type="text" id="isim" name="isim" maxlength="20" required
                           autocomplete="off" placeholder="Adını yaz">

                    <div class="setlist">
                %s        </div>
                  </form>

                  <div class="actions">
                    <a class="btn ghost" href="/ayarla">Kendin ayarla</a>
                    <a class="plain center" href="/tablo">Lider tablosu</a>
                  </div>
                </div>
                """.formatted(allQuestions.size(), cards);

        sendHtml(exchange, 200, Html.page("Kamp Quiz", body));
    }

    /** Kendi kategorini, soru sayini ve sureni sectigin sayfa. */
    private void handleCustom(HttpExchange exchange) throws IOException {
        List<String> categories = QuestionBank.categoriesOf(allQuestions);

        StringBuilder options = new StringBuilder();
        options.append("            <option value=\"\">Hepsi karışık — ")
               .append(allQuestions.size()).append(" soru</option>\n");
        for (String category : categories) {
            int count = QuestionBank.byCategory(allQuestions, category).size();
            options.append("            <option value=\"").append(Html.escape(category)).append("\">")
                   .append(Html.escape(category)).append(" — ").append(count).append(" soru</option>\n");
        }

        String body = """
                <div class="screen">
                  <p class="eyebrow">Serbest tur</p>
                  <h1>Kendin ayarla</h1>

                  <form method="POST" action="/start">
                    <div class="card" style="margin-top:18px">
                      <label class="field" for="isim">Adın</label>
                      <input type="text" id="isim" name="isim" maxlength="20" required
                             autocomplete="off" placeholder="Adını yaz">

                      <label class="field" for="kategori">Kategori</label>
                      <select id="kategori" name="kategori">
                %s          </select>

                      <label class="field" for="adet">Soru sayısı</label>
                      <select id="adet" name="adet">
                        <option value="5">5 soru</option>
                        <option value="10" selected>10 soru</option>
                        <option value="20">20 soru</option>
                      </select>

                      <label class="field" for="sure">Soru başına süre</label>
                      <select id="sure" name="sure" style="margin-bottom:0">
                        <option value="10">10 saniye — hızlı tur</option>
                        <option value="20" selected>20 saniye — normal</option>
                        <option value="45">45 saniye — rahat</option>
                      </select>
                    </div>

                    <div class="actions">
                      <button class="btn" type="submit">Başla</button>
                      <a class="plain center" href="/">Hazır testlere dön</a>
                    </div>
                  </form>
                </div>
                """.formatted(options);

        sendHtml(exchange, 200, Html.page("Kendin ayarla", body));
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

        List<Question> pool;
        int count;
        int seconds;

        QuizSet set = findSet(form.get("set"));
        if (set != null) {
            // Hazir test: sorulari set kendisi secer ve karistirir.
            pool = set.build(allQuestions);
            count = pool.size();
            seconds = set.getTimeLimitSeconds();
        } else {
            String category = form.getOrDefault("kategori", "");
            pool = category.isEmpty()
                    ? allQuestions
                    : QuestionBank.byCategory(allQuestions, category);
            if (pool.isEmpty()) {
                pool = allQuestions;
            }
            count = parseIntOr(form.get("adet"), 10);
            seconds = parseIntOr(form.get("sure"), 20);
        }

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

    /** Sirdaki soruyu ya da az once verilen cevabin sonucunu gosterir. */
    private void handleQuiz(HttpExchange exchange) throws IOException {
        GameSession session = currentSession(exchange);
        if (session == null) {
            redirect(exchange, "/");
            return;
        }

        // Cevap verildiyse once sonuc ekrani gosterilir ("Devam" ile gecilir).
        if (session.getFeedback() != null) {
            sendHtml(exchange, 200, Html.page("Cevap", reviewScreen(session)));
            return;
        }

        Quiz quiz = session.getQuiz();
        if (!quiz.hasNext()) {
            redirect(exchange, "/sonuc");
            return;
        }

        Question question = quiz.currentQuestion();
        String[] options = question.getOptions();
        int limit = quiz.getTimeLimitSeconds();

        // Sayac soru ekrana gelince baslar. Ayni soru icin ikinci cagri sifirlamaz.
        quiz.startQuestionTimer();
        int remaining = quiz.remainingSeconds();

        StringBuilder choices = new StringBuilder();
        for (int i = 0; i < options.length; i++) {
            choices.append("        <label class=\"choice\">")
                   .append("<input type=\"radio\" name=\"cevap\" value=\"").append(i).append("\" required>")
                   .append("<span data-key=\"").append(Html.letter(i)).append("\">")
                   .append(Html.escape(options[i])).append("</span></label>\n");
        }

        int progress = Math.round((quiz.getQuestionNumber() - 1) * 100f / quiz.getTotal());

        String body = """
                <div class="screen">
                  <div class="topbar">
                    <div class="bar"><i style="width:%d%%"></i></div>
                    <div class="clock" id="saat">%d</div>
                  </div>
                  <div class="bar time" id="cubuk" style="margin-bottom:26px">
                    <i id="dolgu" style="width:%d%%"></i>
                  </div>

                  <p class="eyebrow">%s · Soru %d / %d</p>
                  <h2>%s</h2>

                  <form method="POST" action="/cevap" id="cevapForm">
                    <div class="choices">
                %s        </div>
                    <div class="actions">
                      <button class="btn" type="submit">Cevapla</button>
                    </div>
                  </form>
                </div>
                <script>
                  (function () {
                    var toplam = %d, kalan = %d;
                    var saat = document.getElementById('saat');
                    var dolgu = document.getElementById('dolgu');
                    var cubuk = document.getElementById('cubuk');
                    var form = document.getElementById('cevapForm');
                    var sayac = setInterval(function () {
                      kalan--;
                      saat.textContent = kalan < 0 ? 0 : kalan;
                      dolgu.style.width = Math.max(0, kalan / toplam * 100) + '%%';
                      if (kalan <= 5) { cubuk.classList.add('hurry'); saat.classList.add('hurry'); }
                      if (kalan <= 0) {
                        clearInterval(sayac);
                        form.submit();   // sure doldu, bos gonder
                      }
                    }, 1000);
                  })();
                </script>
                """.formatted(
                progress, remaining,
                Math.round(remaining * 100f / limit),
                Html.escape(question.getCategory()),
                quiz.getQuestionNumber(), quiz.getTotal(),
                Html.escape(question.getText()),
                choices,
                limit, remaining);

        sendHtml(exchange, 200, Html.page("Soru " + quiz.getQuestionNumber(), body));
    }

    /** Cevap sonrasi ekrani: dogru sik yesil, secilen yanlis sik kirmizi. */
    private String reviewScreen(GameSession session) {
        GameSession.Feedback fb = session.getFeedback();
        Question question = fb.question();
        String[] options = question.getOptions();

        StringBuilder choices = new StringBuilder();
        for (int i = 0; i < options.length; i++) {
            String state;
            if (question.isCorrect(i)) {
                state = " is-right";
            } else if (i == fb.chosenIndex()) {
                state = " is-wrong";
            } else {
                state = " is-dim";
            }
            choices.append("      <div class=\"choice").append(state).append("\">")
                   .append("<span data-key=\"").append(Html.letter(i)).append("\">")
                   .append(Html.escape(options[i])).append("</span></div>\n");
        }

        String title;
        if (fb.timedOut()) {
            title = "Süre doldu";
        } else if (fb.correct()) {
            title = "Doğru!";
        } else {
            title = "Yanlış";
        }

        String gain = fb.correct() ? "+" + fb.earnedPoints() + " puan" : "+0 puan";
        String why = question.hasExplanation()
                ? "      <div class=\"why\">" + Html.escape(question.getExplanation()) + "</div>\n"
                : "";

        return """
                <div class="screen">
                  <p class="eyebrow">%s</p>
                  <h2>%s</h2>

                  <div class="choices">
                %s      </div>

                  <div class="verdict %s">
                    <h3><span>%s</span><span class="gain">%s</span></h3>
                %s      </div>

                  <div class="actions">
                    <a class="btn %s" href="/devam">Devam</a>
                  </div>
                </div>
                """.formatted(
                Html.escape(question.getCategory()),
                Html.escape(question.getText()),
                choices,
                fb.correct() ? "ok" : "bad",
                title, gain, why,
                fb.correct() ? "" : "blue");
    }

    /** "Devam" tusu: sonucu temizler, sonraki soruya gecer. */
    private void handleContinue(HttpExchange exchange) throws IOException {
        GameSession session = currentSession(exchange);
        if (session == null) {
            redirect(exchange, "/");
            return;
        }
        session.clearFeedback();
        redirect(exchange, session.getQuiz().hasNext() ? "/quiz" : "/sonuc");
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

        session.setFeedback(new GameSession.Feedback(
                result.correct(), result.timedOut(), result.earnedPoints(), question, answer));

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
                scoreboard.save(session.getPlayerName(), quiz.getScore(),
                        quiz.getTotal(), quiz.getPoints());
                session.markScoreSaved();
            } catch (IOException e) {
                System.out.println("Skor kaydedilemedi: " + e.getMessage());
            }
        }

        String body = """
                <div class="screen">
                  <p class="eyebrow">%s</p>
                  <h1>%s</h1>
                  <div class="bigscore">%d</div>
                  <p class="muted small">toplam puan</p>

                  <div class="stats" style="margin-top:22px">
                    <div class="stat"><b>%d/%d</b><span>Doğru</span></div>
                    <div class="stat"><b>%%%d</b><span>Başarı</span></div>
                  </div>

                  <div class="actions">
                    <a class="btn blue" href="/tablo">Lider tablosu</a>
                    <a class="btn ghost" href="/">Yeniden oyna</a>
                  </div>
                </div>
                """.formatted(
                Html.escape(session.getPlayerName()),
                verdictTitle(quiz.getPercentage()),
                quiz.getPoints(),
                quiz.getScore(), quiz.getTotal(), quiz.getPercentage());

        sendHtml(exchange, 200, Html.page("Sonuç", body));
    }

    private static String verdictTitle(int percentage) {
        if (percentage == 100) return "Kusursuz!";
        if (percentage >= 80)  return "Çok iyi";
        if (percentage >= 50)  return "Fena değil";
        return "Bir tur daha?";
    }

    /** Lider tablosu. */
    private void handleBoard(HttpExchange exchange) throws IOException {
        List<Scoreboard.Entry> entries;
        try {
            entries = scoreboard.topScores(20);
        } catch (IOException e) {
            entries = List.of();
        }

        GameSession session = currentSession(exchange);
        String me = session == null ? null : session.getPlayerName();

        StringBuilder rows = new StringBuilder();
        if (entries.isEmpty()) {
            rows.append("      <p class=\"muted center\">Henüz kayıt yok. İlk sen ol.</p>\n");
        } else {
            int rank = 1;
            for (Scoreboard.Entry e : entries) {
                boolean mine = e.name().equals(me);
                rows.append("      <div class=\"row").append(mine ? " me" : "").append("\">")
                    .append("<span class=\"pos\">").append(rank++).append("</span>")
                    .append("<span class=\"who\">").append(Html.escape(e.name())).append("</span>")
                    .append("<span class=\"sub\">").append(e.score()).append("/").append(e.total())
                    .append("</span>")
                    .append("<span class=\"pts\">").append(e.points()).append("</span>")
                    .append("</div>\n");
            }
        }

        String body = """
                <div class="screen">
                  <p class="eyebrow">Sıralama</p>
                  <h1>Lider Tablosu</h1>
                  <p class="muted small">Önce puana, eşitlikte doğru oranına göre.</p>

                  <div class="rank" style="margin-top:20px">
                %s      </div>

                  <div class="actions">
                    <a class="btn" href="/">Ana sayfa</a>
                  </div>
                </div>
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
