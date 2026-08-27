package quiz.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import quiz.ai.QuestionGenerator;
import quiz.core.QuestionBank;
import quiz.core.Quiz;
import quiz.core.QuizSet;
import quiz.core.QuizSetLoader;
import quiz.core.Scoreboard;
import quiz.model.Question;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /** Uretilen paket kaydedilince yeniden yuklendigi icin final degil. */
    private volatile List<Question> allQuestions;
    private volatile List<QuizSet> sets;

    private final Path questionsDir;
    private final Path setsDir;
    private final Scoreboard scoreboard;
    private final QuestionGenerator generator;
    private final int port;

    /** Oyuncu oturumlari. Ayni anda birden fazla istek geldigi icin es zamanli harita. */
    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    /** Acik odalar: kod -> oda. */
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    /**
     * Uretim sayfasinin parolasi. QUIZ_ADMIN_KEY tanimliysa /uret kilitlenir.
     * Anahtar zaten hicbir sayfada gorunmuyor; bu kilit, agdaki baskalarinin
     * senin API kotani harcamasini engellemek icin.
     */
    private final String adminKey = System.getenv("QUIZ_ADMIN_KEY") == null
            ? "" : System.getenv("QUIZ_ADMIN_KEY").trim();
    private final java.util.Set<String> adminTokens = ConcurrentHashMap.newKeySet();
    private final java.util.Random random = new java.util.Random();

    public WebServer(List<Question> allQuestions, List<QuizSet> sets,
                     Path questionsDir, Path setsDir, Scoreboard scoreboard, int port) {
        this.allQuestions = allQuestions;
        this.sets = sets;
        this.questionsDir = questionsDir;
        this.setsDir = setsDir;
        this.scoreboard = scoreboard;
        this.generator = new QuestionGenerator();
        this.port = port;
    }

    /** Yeni paket kaydedildikten sonra sorulari ve setleri diskten tazeler. */
    private void reloadContent() throws IOException {
        this.allQuestions = QuestionBank.loadFromDirectory(questionsDir);
        this.sets = QuizSetLoader.loadFromDirectory(setsDir);
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
        server.createContext("/kur", this::handleHostSetup);
        server.createContext("/oda", this::handleHostPanel);
        server.createContext("/ekran", this::handleScreen);
        server.createContext("/katil", this::handleJoin);
        server.createContext("/uret", this::handleGenerate);
        server.createContext("/tekrar", this::handleRetry);
        server.createContext("/rapor", this::handleReport);
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

                  <form method="POST" action="/katil" class="joinbox">
                    <label class="field" for="kod">Oda kodun varsa</label>
                    <div class="joinrow">
                      <input type="text" id="kod" name="kod" inputmode="numeric" maxlength="4"
                             pattern="[0-9]{4}" placeholder="0000" class="codeinput" required>
                      <input type="text" name="isim" maxlength="20" required
                             autocomplete="off" placeholder="Adın">
                      <button class="btn blue" type="submit">Katıl</button>
                    </div>
                  </form>

                  <p class="divider"><span>ya da tek başına</span></p>

                  <form method="POST" action="/start" style="margin-top:22px">
                    <label class="field" for="isim">Adın</label>
                    <input type="text" id="isim" name="isim" maxlength="20" required
                           autocomplete="off" placeholder="Adını yaz">

                    <div class="setlist">
                %s        </div>
                  </form>

                  <div class="actions">
                    <a class="btn ghost" href="/ayarla">Kendin ayarla</a>
                    <a class="plain center" href="/kur">Oda kur (sunum modu)</a>
                    <a class="plain center" href="/uret">AI ile soru paketi üret</a>
                    <a class="plain center" href="/tablo">Lider tablosu</a>
                  </div>
                </div>
                """.formatted(allQuestions.size(), cards);

        sendHtml(exchange, 200, Html.page("Kamp Quiz", body));
    }

    // -------------------------------------------------- tekrar modu ve rapor

    /** Yanlis yapilan sorulari yeni bir tur olarak sunar. */
    private void handleRetry(HttpExchange exchange) throws IOException {
        String sessionId = currentSessionId(exchange);
        GameSession previous = sessionId == null ? null : sessions.get(sessionId);
        if (previous == null) {
            redirect(exchange, "/");
            return;
        }

        List<Question> wrong = previous.getQuiz().getWrongQuestions();
        if (wrong.isEmpty()) {
            redirect(exchange, "/sonuc");
            return;
        }

        Quiz retry = new Quiz(wrong);
        retry.shuffle();
        retry.setTimeLimitSeconds(previous.getQuiz().getTimeLimitSeconds());

        // Tekrar turu odanin siralamasina KATILMAZ; yoksa oda tablosu bozulurdu.
        sessions.put(sessionId, new GameSession(previous.getPlayerName(), retry, null));
        redirect(exchange, "/quiz");
    }

    /** Hoca icin yanlis raporu: hangi soru en cok yanlis yapildi. */
    private void handleReport(HttpExchange exchange) throws IOException {
        Room room = rooms.get(query(exchange, "kod"));
        if (room == null) {
            redirect(exchange, "/kur");
            return;
        }

        // Soru metni -> [soruldu, yanlis]  +  temsil eden soru nesnesi
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, Question> byText = new LinkedHashMap<>();

        for (GameSession player : room.standings()) {
            for (Quiz.AnswerResult result : player.getQuiz().getHistory()) {
                String key = result.question().getText();
                byText.putIfAbsent(key, result.question());
                int[] tally = counts.computeIfAbsent(key, k -> new int[2]);
                tally[0]++;
                if (!result.correct()) {
                    tally[1]++;
                }
            }
        }

        List<Map.Entry<String, int[]>> rows = new ArrayList<>(counts.entrySet());
        rows.sort((a, b) -> {
            double ra = a.getValue()[1] / (double) a.getValue()[0];
            double rb = b.getValue()[1] / (double) b.getValue()[0];
            return Double.compare(rb, ra);   // en cok yanlis yapilan basa
        });

        StringBuilder list = new StringBuilder();
        if (rows.isEmpty()) {
            list.append("      <p class=\"muted center\">Henüz cevaplanmış soru yok.</p>\n");
        } else {
            for (Map.Entry<String, int[]> row : rows) {
                int asked = row.getValue()[0];
                int wrong = row.getValue()[1];
                int percent = Math.round(wrong * 100f / asked);
                Question question = byText.get(row.getKey());

                list.append("      <div class=\"card\" style=\"margin-bottom:12px\">")
                    .append("<div class=\"missbar\"><span style=\"width:").append(percent)
                    .append("%\"></span></div>")
                    .append("<p class=\"missmeta\"><b>%").append(percent)
                    .append(" yanlış</b> · ").append(wrong).append("/").append(asked)
                    .append(" kişi</p>")
                    .append("<p style=\"margin:6px 0 8px\">").append(Html.escape(question.getText()))
                    .append("</p>")
                    .append("<p class=\"muted small\" style=\"margin:0\">Doğru: <b>")
                    .append(Html.escape(question.getCorrectOption())).append("</b></p>");
                if (question.hasExplanation()) {
                    list.append("<p class=\"muted small\" style=\"margin:6px 0 0\">")
                        .append(Html.escape(question.getExplanation())).append("</p>");
                }
                list.append("</div>\n");
            }
        }

        String body = """
                <div class="screen wide">
                  <p class="eyebrow">%s · oda %s</p>
                  <h1>Yanlış raporu</h1>
                  <p class="muted small">En çok yanlış yapılan soru başta. Dersi buradan
                  toparlamak en verimlisi.</p>

                  <div style="margin-top:20px">
                %s      </div>

                  <div class="actions">
                    <a class="btn blue" href="/ekran?kod=%s">Canlı sıralama</a>
                    <a class="btn ghost" href="/oda?kod=%s">Oda paneli</a>
                  </div>
                </div>
                """.formatted(
                Html.escape(room.getSet().getName()), room.getCode(), list,
                room.getCode(), room.getCode());

        sendHtml(exchange, 200, Html.page("Yanlış raporu", body));
    }

    // ------------------------------------------------------------ AI uretici

    /** Soru paketi uretme, AI ile duzenleme ve kaydetme sayfasi. */
    private void handleGenerate(HttpExchange exchange) throws IOException {
        if (!generator.isEnabled()) {
            sendHtml(exchange, 200, Html.page("AI kapalı", """
                    <div class="screen">
                      <p class="eyebrow">Soru üretici</p>
                      <h1>AI özelliği kapalı</h1>
                      <div class="card">
                        <p class="muted small">Bu özellik bir Google Gemini API anahtarı gerektirir.
                        Anahtar <b>koda yazılmaz</b>, ortam değişkeninden okunur:</p>
                        <pre class="code-block">export GEMINI_API_KEY="buraya-anahtar"
./run.sh web</pre>
                        <p class="muted small">Ya da OpenRouter (daha ucuz modeller):</p>
                        <pre class="code-block">export OPENROUTER_API_KEY="buraya-anahtar"
export OPENROUTER_MODEL="saglayici/model"
./run.sh web</pre>
                        <p class="muted small">Windows PowerShell'de <code>export</code> yerine
                        <code>$env:AD="deger"</code> yazılır. Anahtarı asla depoya ekleme.</p>
                      </div>
                      <div class="actions"><a class="btn" href="/">Ana sayfa</a></div>
                    </div>
                    """));
            return;
        }

        // Parola tanimliysa once giris istenir.
        if (!adminKey.isEmpty() && !isAdmin(exchange)) {
            if ("POST".equals(exchange.getRequestMethod())) {
                String given = readForm(exchange).getOrDefault("parola", "");
                if (java.security.MessageDigest.isEqual(
                        given.getBytes(StandardCharsets.UTF_8),
                        adminKey.getBytes(StandardCharsets.UTF_8))) {
                    String token = UUID.randomUUID().toString();
                    adminTokens.add(token);
                    exchange.getResponseHeaders().add("Set-Cookie",
                            "qadmin=" + token + "; Path=/; Max-Age=28800; SameSite=Lax");
                    redirect(exchange, "/uret");
                    return;
                }
                sendHtml(exchange, 200, Html.page("Giriş", adminForm("Parola yanlış.")));
                return;
            }
            sendHtml(exchange, 200, Html.page("Giriş", adminForm(null)));
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod())) {
            sendHtml(exchange, 200, Html.page("Soru üret", generateForm(null)));
            return;
        }

        Map<String, String> form = readForm(exchange);
        String action = form.getOrDefault("islem", "uret");
        String title = form.getOrDefault("baslik", "").trim();

        try {
            switch (action) {
                case "duzenle" -> {
                    String draft = form.getOrDefault("taslak", "");
                    String instruction = form.getOrDefault("talimat", "").trim();
                    if (instruction.isEmpty()) {
                        sendHtml(exchange, 200, Html.page("Taslak",
                                draftEditor(title, draft, "Ne değiştirmemi istediğini yaz.")));
                        return;
                    }
                    String revised = generator.revise(draft, instruction);
                    sendHtml(exchange, 200, Html.page("Taslak", draftEditor(title, revised, null)));
                }
                case "kaydet" -> {
                    String draft = form.getOrDefault("taslak", "");
                    Path saved = saveDraft(title, draft);
                    reloadContent();
                    sendHtml(exchange, 200, Html.page("Kaydedildi", savedScreen(title, saved)));
                }
                default -> {
                    String topic = form.getOrDefault("konu", "").trim();
                    if (topic.isEmpty()) {
                        sendHtml(exchange, 200, Html.page("Soru üret", generateForm("Bir konu yaz.")));
                        return;
                    }
                    int count = Math.max(3, Math.min(25, parseIntOr(form.get("adet"), 10)));
                    String level = form.getOrDefault("seviye", "giriş");
                    String draft = generator.generate(topic, count, level);
                    sendHtml(exchange, 200, Html.page("Taslak",
                            draftEditor(title.isEmpty() ? topic : title, draft, null)));
                }
            }
        } catch (IOException e) {
            sendHtml(exchange, 200, Html.page("Hata", generateForm(e.getMessage())));
        } catch (IllegalArgumentException e) {
            sendHtml(exchange, 200, Html.page("Hata",
                    draftEditor(title, form.getOrDefault("taslak", ""), e.getMessage())));
        }
    }

    /** Cerezdeki yonetici belirteci gecerli mi? */
    private boolean isAdmin(HttpExchange exchange) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) {
            return false;
        }
        for (String header : cookies) {
            for (String cookie : header.split(";")) {
                String[] pair = cookie.trim().split("=", 2);
                if (pair.length == 2 && "qadmin".equals(pair[0]) && adminTokens.contains(pair[1])) {
                    return true;
                }
            }
        }
        return false;
    }

    private String adminForm(String error) {
        String warning = error == null ? ""
                : "  <div class=\"verdict bad\"><h3><span>" + Html.escape(error) + "</span></h3></div>\n";
        return """
                <div class="screen">
                  <p class="eyebrow">Soru üretici</p>
                  <h1>Parola gerekli</h1>
                  <p class="muted small">Bu sayfa API kotası harcadığı için korumalı.</p>
                %s
                  <form method="POST" action="/uret">
                    <div class="card">
                      <label class="field" for="parola">Parola</label>
                      <input type="password" id="parola" name="parola" required
                             autocomplete="off" style="margin-bottom:0">
                    </div>
                    <div class="actions">
                      <button class="btn" type="submit">Giriş</button>
                      <a class="plain center" href="/">Ana sayfa</a>
                    </div>
                  </form>
                </div>
                """.formatted(warning);
    }

    private String generateForm(String error) {
        String warning = error == null ? ""
                : "  <div class=\"verdict bad\"><h3><span>Olmadı</span></h3>"
                  + "<div class=\"why\">" + Html.escape(error) + "</div></div>\n";

        if (adminKey.isEmpty()) {
            warning += "  <div class=\"notice\">Bu sayfa parolasız. Ağdaki herkes kota "
                     + "harcayabilir. Korumak için QUIZ_ADMIN_KEY ortam değişkenini tanımla.</div>\n";
        }

        return """
                <div class="screen">
                  <p class="eyebrow">Soru üretici · %s</p>
                  <h1>Yeni paket üret</h1>
                %s
                  <form method="POST" action="/uret">
                    <input type="hidden" name="islem" value="uret">
                    <div class="card">
                      <label class="field" for="konu">Konu</label>
                      <input type="text" id="konu" name="konu" required autocomplete="off"
                             placeholder="örn. Siber güvenlik temelleri">

                      <label class="field" for="baslik">Paket adı</label>
                      <input type="text" id="baslik" name="baslik" autocomplete="off"
                             placeholder="boş bırakırsan konu adı kullanılır">

                      <label class="field" for="adet">Soru sayısı</label>
                      <select id="adet" name="adet">
                        <option value="5">5 soru</option>
                        <option value="10" selected>10 soru</option>
                        <option value="15">15 soru</option>
                        <option value="20">20 soru</option>
                      </select>

                      <label class="field" for="seviye">Seviye</label>
                      <select id="seviye" name="seviye" style="margin-bottom:0">
                        <option value="giriş" selected>Giriş</option>
                        <option value="orta">Orta</option>
                        <option value="ileri">İleri</option>
                      </select>
                    </div>

                    <div class="actions">
                      <button class="btn" type="submit">Üret</button>
                      <a class="plain center" href="/">Ana sayfa</a>
                    </div>
                  </form>
                </div>
                """.formatted(Html.escape(generator.describe()), warning);
    }

    /** Uretilen taslagi gosterir: elle duzenlenebilir, AI ile de duzenlenebilir. */
    private String draftEditor(String title, String draft, String error) {
        String warning = error == null ? ""
                : "  <div class=\"verdict bad\"><h3><span>Olmadı</span></h3>"
                  + "<div class=\"why\">" + Html.escape(error) + "</div></div>\n";

        int lines = 0;
        for (String line : draft.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#") && !t.startsWith(">")) {
                lines++;
            }
        }

        // Kutu icerige gore buyusun; sabit yukseklik bos alan birakiyor.
        int rows = Math.max(8, Math.min(26, draft.split("\\n").length + 2));

        return """
                <div class="screen">
                  <p class="eyebrow">Taslak · %d soru</p>
                  <h1>%s</h1>
                  <p class="muted small">İstediğin satırı elle düzeltebilir ya da aşağıdan AI'ya
                  düzelttirebilirsin. Kaydetmeden önce paket doğrulanır.</p>
                %s
                  <form method="POST" action="/uret">
                    <input type="hidden" name="baslik" value="%s">
                    <textarea name="taslak" class="draft" spellcheck="false" rows="%d">%s</textarea>

                    <div class="card" style="margin-top:14px">
                      <label class="field" for="talimat">AI ile düzenle</label>
                      <input type="text" id="talimat" name="talimat" autocomplete="off"
                             style="margin-bottom:12px"
                             placeholder="örn. soruları biraz kolaylaştır, 3. soruyu değiştir">
                      <button class="btn blue" type="submit" name="islem" value="duzenle">AI ile düzenle</button>
                    </div>

                    <div class="actions">
                      <button class="btn" type="submit" name="islem" value="kaydet">Pakete kaydet</button>
                      <a class="plain center" href="/uret">Baştan üret</a>
                    </div>
                  </form>
                </div>
                """.formatted(lines, Html.escape(title), warning, Html.escape(title),
                              rows, Html.escape(draft));
    }

    private String savedScreen(String title, Path file) {
        return """
                <div class="screen">
                  <p class="eyebrow">Kaydedildi</p>
                  <h1>%s hazır</h1>
                  <div class="card">
                    <p class="muted small">Dosya: <b>%s</b></p>
                    <p class="muted small">Paket hemen kullanılabilir. Katkı olarak göndermek için
                    bu dosyayı commit edip PR açman yeterli.</p>
                  </div>
                  <div class="actions">
                    <a class="btn" href="/">Ana sayfa</a>
                    <a class="btn ghost" href="/uret">Bir paket daha üret</a>
                  </div>
                </div>
                """.formatted(Html.escape(title), Html.escape(file.toString()));
    }

    /**
     * Taslagi dosyaya yazar. Once GECICI bir dosyaya yazip QuestionBank ile
     * okutur; gecerli soru cikmazsa hicbir sey kaydedilmez.
     */
    private Path saveDraft(String title, String draft) throws IOException {
        String safeTitle = title.isBlank() ? "Üretilen Paket" : title.trim();
        String content = "# baslik: " + safeTitle + "\n"
                + "# Bu paket AI ile üretildi, insan gözüyle kontrol edilmelidir.\n\n"
                + draft.strip() + "\n";

        Path temp = Files.createTempFile("quiz-taslak", ".txt");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            int count = QuestionBank.loadFromFile(temp).size();
            if (count == 0) {
                throw new IllegalArgumentException(
                        "Taslakta geçerli soru bulunamadı. Biçimi kontrol et: soru | şık | şık | şık | şık | doğruNo");
            }

            Path target = uniquePath(slug(safeTitle));
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return target;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Dosya adi olarak guvenli bir metin uretir (dizin gecisi engellenir). */
    private static String slug(String text) {
        String lower = text.toLowerCase(java.util.Locale.forLanguageTag("tr"))
                .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u')
                .replace('ş', 's').replace('ö', 'o').replace('ç', 'c');
        StringBuilder out = new StringBuilder();
        for (char c : lower.toCharArray()) {
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
                out.append(c);
            } else if (!out.isEmpty() && out.charAt(out.length() - 1) != '-') {
                out.append('-');
            }
        }
        String slug = out.toString();
        while (slug.endsWith("-")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        if (slug.isEmpty()) {
            slug = "uretilen";
        }
        return slug.length() > 40 ? slug.substring(0, 40) : slug;
    }

    /** Ayni adda dosya varsa sonuna sayi ekler. */
    private Path uniquePath(String slug) {
        Path candidate = questionsDir.resolve(slug + ".txt");
        int n = 2;
        while (Files.exists(candidate)) {
            candidate = questionsDir.resolve(slug + "-" + n++ + ".txt");
        }
        return candidate;
    }

    // ------------------------------------------------------------------ odalar

    /** Hocanin oda actigi sayfa: hazir testlerden birini secer. */
    private void handleHostSetup(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            QuizSet set = findSet(readForm(exchange).get("set"));
            if (set == null) {
                redirect(exchange, "/kur");
                return;
            }
            Room room = new Room(newRoomCode(), set);
            rooms.put(room.getCode(), room);
            redirect(exchange, "/oda?kod=" + room.getCode());
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

        String body = """
                <div class="screen">
                  <p class="eyebrow">Sunum modu</p>
                  <h1>Oda kur</h1>
                  <p class="muted small">Bir test seç. Katılımcılara 4 haneli bir kod vereceğiz.</p>

                  <form method="POST" action="/kur" style="margin-top:20px">
                    <div class="setlist">
                %s        </div>
                  </form>

                  <div class="actions">
                    <a class="plain center" href="/">Ana sayfaya dön</a>
                  </div>
                </div>
                """.formatted(cards);

        sendHtml(exchange, 200, Html.page("Oda kur", body));
    }

    /** Hocanin paneli: kod, katilimcilar ve projeksiyon baglantisi. */
    private void handleHostPanel(HttpExchange exchange) throws IOException {
        Room room = rooms.get(query(exchange, "kod"));
        if (room == null) {
            redirect(exchange, "/kur");
            return;
        }

        StringBuilder list = new StringBuilder();
        if (room.playerCount() == 0) {
            list.append("      <p class=\"muted center small\">Henüz kimse katılmadı.</p>\n");
        } else {
            for (GameSession player : room.standings()) {
                list.append("      <div class=\"row\"><span class=\"who\">")
                    .append(Html.escape(player.getPlayerName()))
                    .append("</span><span class=\"sub\">")
                    .append(progressLabel(player))
                    .append("</span><span class=\"pts\">")
                    .append(player.getQuiz().getPoints()).append("</span></div>\n");
            }
        }

        String body = """
                <div class="screen">
                  <p class="eyebrow">%s</p>
                  <h1>Oda açık</h1>
                  <div class="code">%s</div>
                  <p class="muted small center">Katılımcılar ana sayfada bu kodu ve adını yazsın,
                  ya da aşağıdaki kareyi okutsun.</p>

                  <div class="qr">%s<span>%s</span></div>

                  <div class="stats" style="margin-top:22px">
                    <div class="stat"><b>%d</b><span>Katılımcı</span></div>
                    <div class="stat"><b>%d</b><span>Soru</span></div>
                  </div>

                  <div class="rank">
                %s      </div>

                  <div class="actions">
                    <a class="btn blue" href="/ekran?kod=%s">Projeksiyon ekranı</a>
                    <a class="btn ghost" href="/rapor?kod=%s">Yanlış raporu</a>
                    <a class="plain center" href="/">Ana sayfa</a>
                  </div>
                </div>
                """.formatted(
                Html.escape(room.getSet().getName()),
                room.getCode(),
                joinQr(exchange, 190),
                Html.escape(joinUrl(exchange)),
                room.playerCount(),
                room.getSet().totalQuestions(),
                list,
                room.getCode(), room.getCode());

        sendHtml(exchange, 200, Html.page("Oda " + room.getCode(), body,
                "  <meta http-equiv=\"refresh\" content=\"4\">\n"));
    }

    /** Buyuk ekranda gosterilen canli siralama. Kendi kendine yenilenir. */
    private void handleScreen(HttpExchange exchange) throws IOException {
        Room room = rooms.get(query(exchange, "kod"));
        if (room == null) {
            redirect(exchange, "/kur");
            return;
        }

        StringBuilder list = new StringBuilder();
        List<GameSession> standings = room.standings();
        if (standings.isEmpty()) {
            list.append("      <p class=\"muted center\">Katılımcılar bekleniyor...</p>\n");
        } else {
            int rank = 1;
            for (GameSession player : standings) {
                list.append("      <div class=\"row\"><span class=\"pos\">").append(rank++)
                    .append("</span><span class=\"who\">").append(Html.escape(player.getPlayerName()))
                    .append("</span><span class=\"sub\">").append(progressLabel(player))
                    .append("</span><span class=\"pts\">").append(player.getQuiz().getPoints())
                    .append("</span></div>\n");
            }
        }

        String body = """
                <div class="screen wide">
                  <div class="screenhead">
                    <div>
                      <p class="eyebrow">%s</p>
                      <h1>Canlı Sıralama</h1>
                    </div>
                    <div class="codebox">
                      <span>Katılım kodu</span>
                      <b>%s</b>
                      <div class="qr small">%s<span>%s</span></div>
                    </div>
                  </div>

                  <div class="rank big">
                %s      </div>

                  <p class="muted small center" style="margin-top:20px">%d katılımcı · %s</p>
                </div>
                """.formatted(
                Html.escape(room.getSet().getName()),
                room.getCode(),
                joinQr(exchange, 150),
                Html.escape(joinUrl(exchange)),
                list,
                room.playerCount(),
                room.everyoneFinished() ? "test bitti" : "devam ediyor");

        sendHtml(exchange, 200, Html.page("Ekran " + room.getCode(), body,
                "  <meta http-equiv=\"refresh\" content=\"3\">\n"));
    }

    /** Katilimci oda koduyla girer. */
    private void handleJoin(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            redirect(exchange, "/");
            return;
        }

        Map<String, String> form = readForm(exchange);
        Room room = rooms.get(form.getOrDefault("kod", "").trim());
        if (room == null) {
            sendHtml(exchange, 404, Html.page("Oda bulunamadı", """
                    <div class="screen">
                      <div class="card center">
                        <h1>Oda bulunamadı</h1>
                        <p class="muted">Kodu kontrol et. Oda kapanmış da olabilir.</p>
                      </div>
                      <div class="actions"><a class="btn" href="/">Geri dön</a></div>
                    </div>
                    """));
            return;
        }

        String name = cleanName(form.get("isim"));
        GameSession session = new GameSession(name, room.newQuiz(allQuestions), room.getCode());
        room.addPlayer(session);

        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, session);
        setSessionCookie(exchange, sessionId);
        redirect(exchange, "/quiz");
    }

    /**
     * Katilim adresini uretir. Tarayicinin gonderdigi Host basligini kullanir;
     * boylece hocanin adres cubuğunda ne yaziyorsa QR'da da o cikar.
     * Hoca localhost uzerinden acmissa telefonlar oraya baglanamayacagi icin
     * yerel ag adresine cevrilir.
     */
    private String joinUrl(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host == null || host.startsWith("localhost") || host.startsWith("127.0.0.1")) {
            List<String> addresses = localAddresses();
            host = addresses.isEmpty() ? "localhost:" + port : addresses.get(0) + ":" + port;
        }
        return "http://" + host + "/";
    }

    /** Katilim adresi icin QR kodu; uretilemezse bos dizge. */
    private String joinQr(HttpExchange exchange, int pixels) {
        try {
            return QrCode.encode(joinUrl(exchange)).toSvg(pixels, "#0d1117", "#f0f7fa");
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** "Soru 3/15" ya da "bitti" seklinde ilerleme etiketi. */
    private static String progressLabel(GameSession player) {
        Quiz quiz = player.getQuiz();
        return quiz.hasNext()
                ? quiz.getQuestionNumber() + "/" + quiz.getTotal()
                : "bitti " + quiz.getScore() + "/" + quiz.getTotal();
    }

    /** Kullanilmayan 4 haneli bir oda kodu uretir. */
    private String newRoomCode() {
        for (int deneme = 0; deneme < 200; deneme++) {
            String code = String.format("%04d", random.nextInt(10000));
            if (!rooms.containsKey(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Boş oda kodu bulunamadı.");
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
        String name = cleanName(form.get("isim"));

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
        sessions.put(sessionId, new GameSession(name, quiz, null));
        setSessionCookie(exchange, sessionId);
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
                %s      <a class="btn blue" href="/tablo">Lider tablosu</a>
                    <a class="btn ghost" href="/">Yeniden oyna</a>
                  </div>
                </div>
                """.formatted(
                Html.escape(session.getPlayerName()),
                verdictTitle(quiz.getPercentage()),
                quiz.getPoints(),
                quiz.getScore(), quiz.getTotal(), quiz.getPercentage(),
                retryButton(quiz));

        sendHtml(exchange, 200, Html.page("Sonuç", body));
    }

    /** Yanlis varsa "tekrar coz" butonu. */
    private static String retryButton(Quiz quiz) {
        int wrong = quiz.getWrongQuestions().size();
        return wrong == 0 ? ""
                : "    <a class=\"btn\" href=\"/tekrar\">" + wrong
                  + " yanlışını tekrar çöz</a>\n";
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

    /** URL'deki ?anahtar=deger degerini okur. */
    private static String query(HttpExchange exchange, String key) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) {
            return "";
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && key.equals(pair.substring(0, eq))) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private static String cleanName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            name = "Misafir";
        }
        return name.length() > 20 ? name.substring(0, 20) : name;
    }

    private void setSessionCookie(HttpExchange exchange, String sessionId) {
        exchange.getResponseHeaders().add("Set-Cookie",
                COOKIE_NAME + "=" + sessionId + "; Path=/; Max-Age=7200; SameSite=Lax");
    }

    /** Cerezdeki oturum kimligini dondurur. */
    private String currentSessionId(HttpExchange exchange) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) {
            return null;
        }
        for (String header : cookies) {
            for (String cookie : header.split(";")) {
                String[] pair = cookie.trim().split("=", 2);
                if (pair.length == 2 && COOKIE_NAME.equals(pair[0])) {
                    return pair[1];
                }
            }
        }
        return null;
    }

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
