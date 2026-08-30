package quiz.web;

import com.sun.net.httpserver.HttpExchange;
import quiz.ai.QuestionGenerator;
import quiz.core.QuestionBank;
import quiz.core.QuizSet;
import quiz.core.QuizSetLoader;
import quiz.core.Scoreboard;
import quiz.model.Question;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sunucunun tum sayfalari arasinda PAYLASILAN durum ve yardimci metotlar.
 *
 * Eskiden bunlarin hepsi WebServer sinifinin alanlariydi. WebServer buyudukce
 * okunmasi zorlastigi icin, sayfa mantigi ayri siniflara (pages/ altina)
 * tasindi; bu sinif da o sayfalarin ihtiyac duydugu ortak seyleri (soru
 * havuzu, acik odalar, oturumlar, kucuk yardimci metotlar) tek yerde tutar.
 *
 * Bir sayfa sinifi (orn. QuizPages) constructor'inda bir ServerContext alir
 * ve ihtiyaci olan her seye oradan ulasir.
 */
final class ServerContext {

    private static final String COOKIE_NAME = "qsid";
    private static final String HOST_COOKIE_NAME = "qhost";

    /** Uretilen paket kaydedilince yeniden yuklendigi icin final degil. */
    private volatile List<Question> allQuestions;
    private volatile List<QuizSet> sets;

    private final Path questionsDir;
    private final Path setsDir;
    private final Scoreboard scoreboard;
    private final QuestionGenerator generator;
    private final int port;
    private volatile int boundPort;

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
    private final Set<String> adminTokens = ConcurrentHashMap.newKeySet();
    private final Random random = new Random();

    ServerContext(List<Question> allQuestions, List<QuizSet> sets,
                  Path questionsDir, Path setsDir, Scoreboard scoreboard, int port) {
        this.allQuestions = allQuestions;
        this.sets = sets;
        this.questionsDir = questionsDir;
        this.setsDir = setsDir;
        this.scoreboard = scoreboard;
        this.generator = new QuestionGenerator();
        this.port = port;
    }

    // --------------------------------------------------------------- durum

    List<Question> getAllQuestions() {
        return allQuestions;
    }

    void setAllQuestions(List<Question> allQuestions) {
        this.allQuestions = allQuestions;
    }

    List<QuizSet> getSets() {
        return sets;
    }

    void setSets(List<QuizSet> sets) {
        this.sets = sets;
    }

    Path getQuestionsDir() {
        return questionsDir;
    }

    Path getSetsDir() {
        return setsDir;
    }

    Scoreboard getScoreboard() {
        return scoreboard;
    }

    QuestionGenerator getGenerator() {
        return generator;
    }

    int getPort() {
        return boundPort > 0 ? boundPort : port;
    }

    void setBoundPort(int boundPort) {
        this.boundPort = boundPort;
    }

    String getAdminKey() {
        return adminKey;
    }

    Map<String, GameSession> getSessions() {
        return sessions;
    }

    Map<String, Room> getRooms() {
        return rooms;
    }

    Set<String> getAdminTokens() {
        return adminTokens;
    }

    /** Oda yoneticisinin tarayicisina verilecek tahmin edilemez belirteci uretir. */
    String newHostToken() {
        return UUID.randomUUID().toString();
    }

    /** Yeni paket kaydedildikten sonra sorulari ve setleri diskten tazeler. */
    void reloadContent() throws IOException {
        // core paketi ekrani bilmez; uyarilari toplayip BURADA basiyoruz.
        List<String> warnings = new ArrayList<>();
        setAllQuestions(QuestionBank.loadFromDirectory(questionsDir, warnings));
        setSets(QuizSetLoader.loadFromDirectory(setsDir, warnings));
        for (String warning : warnings) {
            System.out.println("  [UYARI] " + warning);
        }
    }

    /** Adiyla bir hazir seti bulur. */
    QuizSet findSet(String name) {
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

    /** Kullanilmayan 4 haneli bir oda kodu uretir. */
    String newRoomCode() {
        for (int deneme = 0; deneme < 200; deneme++) {
            String code = String.format("%04d", random.nextInt(10000));
            if (!rooms.containsKey(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Boş oda kodu bulunamadı.");
    }

    // ------------------------------------------------------------ oturumlar

    void setSessionCookie(HttpExchange exchange, String sessionId) {
        exchange.getResponseHeaders().add("Set-Cookie",
                COOKIE_NAME + "=" + sessionId + "; Path=/; Max-Age=7200; SameSite=Lax");
    }

    /** Cerezdeki oturum kimligini dondurur. */
    String currentSessionId(HttpExchange exchange) {
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
    GameSession currentSession(HttpExchange exchange) {
        String id = currentSessionId(exchange);
        return id == null ? null : sessions.get(id);
    }

    void setHostCookie(HttpExchange exchange, String token) {
        exchange.getResponseHeaders().add("Set-Cookie",
                HOST_COOKIE_NAME + "=" + token + "; Path=/; Max-Age=7200; SameSite=Lax; HttpOnly");
    }

    /** Cerezdeki oda yoneticisi belirtecini dondurur. */
    String currentHostToken(HttpExchange exchange) {
        return cookieValue(exchange, HOST_COOKIE_NAME);
    }

    /** Oda kodunu bilen oyuncu ile odayi kuran yoneticiyi birbirinden ayirir. */
    boolean isHost(HttpExchange exchange, Room room) {
        return room != null && room.isHostToken(currentHostToken(exchange));
    }

    private static String cookieValue(HttpExchange exchange, String cookieName) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) {
            return null;
        }
        for (String header : cookies) {
            for (String cookie : header.split(";")) {
                String[] pair = cookie.trim().split("=", 2);
                if (pair.length == 2 && cookieName.equals(pair[0])) {
                    return pair[1];
                }
            }
        }
        return null;
    }

    /** Cerezdeki yonetici belirteci gecerli mi? */
    boolean isAdmin(HttpExchange exchange) {
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

    // --------------------------------------------------------------- istek

    /** URL'deki ?anahtar=deger degerini okur. */
    static String query(HttpExchange exchange, String key) {
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

    /** POST govdesindeki 'a=1&b=2' bicimini haritaya cevirir. */
    Map<String, String> readForm(HttpExchange exchange) throws IOException {
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

    static int parseIntOr(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static String cleanName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            name = "Misafir";
        }
        return name.length() > 20 ? name.substring(0, 20) : name;
    }

    // ---------------------------------------------------------------- yanit

    void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(303, -1);   // 303 = "gordum, simdi suraya git"
        exchange.close();
    }

    void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        send(exchange, status, "text/html; charset=UTF-8", html);
    }

    void sendHostRequired(HttpExchange exchange) throws IOException {
        sendHtml(exchange, 403, Html.page("Yönetici erişimi gerekli", """
                <div class="screen">
                  <div class="card center">
                    <h1>Yönetici erişimi gerekiyor</h1>
                    <p class="muted">Bu ekranı yalnızca odayı kuran tarayıcı açabilir.</p>
                  </div>
                  <div class="actions"><a class="btn" href="/">Ana sayfaya dön</a></div>
                </div>
                """));
    }

    void send(HttpExchange exchange, int status, String contentType, String content)
            throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    // ------------------------------------------------------------- katilim

    /**
     * Katilim adresini uretir. Tarayicinin gonderdigi Host basligini kullanir;
     * boylece hocanin adres cubuğunda ne yaziyorsa QR'da da o cikar.
     * Hoca localhost uzerinden acmissa telefonlar oraya baglanamayacagi icin
     * yerel ag adresine cevrilir.
     */
    String joinUrl(HttpExchange exchange, String roomCode) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host == null || host.startsWith("localhost") || host.startsWith("127.0.0.1")) {
            String address = preferredLocalAddress();
            host = address == null ? "localhost:" + getPort() : address + ":" + getPort();
        }
        return "http://" + host + "/katil?kod=" + roomCode;
    }

    /** Katilim adresi icin QR kodu; uretilemezse bos dizge. */
    String joinQr(HttpExchange exchange, int pixels, String roomCode) {
        try {
            return QrCode.encode(joinUrl(exchange, roomCode)).toSvg(pixels, "#0d1117", "#f0f7fa");
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** "Soru 3/15" ya da "bitti" seklinde ilerleme etiketi. */
    static String progressLabel(GameSession player) {
        var quiz = player.getQuiz();
        return quiz.hasNext()
                ? quiz.getQuestionNumber() + "/" + quiz.getTotal()
                : "bitti " + quiz.getScore() + "/" + quiz.getTotal();
    }

    /** Bilgisayarin yerel agdaki IPv4 adreslerini bulur. */
    static List<String> localAddresses() {
        List<String> found = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || isVirtualInterface(nic)) {
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

    /** Bazı Windows sürücüleri sanal adaptörü isVirtual() ile işaretlemez. */
    private static boolean isVirtualInterface(NetworkInterface nic) {
        if (nic.isVirtual()) {
            return true;
        }
        String name = ((nic.getName() == null ? "" : nic.getName()) + " "
                + (nic.getDisplayName() == null ? "" : nic.getDisplayName()))
                .toLowerCase(java.util.Locale.ROOT);
        return name.contains("virtualbox") || name.contains("vmware")
                || name.contains("hyper-v") || name.contains("host-only")
                || name.contains("wsl") || name.contains("docker");
    }

    /** UDP connect paket gondermeden varsayilan rotanin yerel adresini verir. */
    private static String preferredLocalAddress() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 53);
            InetAddress address = socket.getLocalAddress();
            String ip = address.getHostAddress();
            if (address.isSiteLocalAddress() && !address.isLoopbackAddress()
                    && !address.isAnyLocalAddress() && !ip.contains(":")) {
                return ip;
            }
        } catch (Exception ignored) {
            // Asagidaki liste, varsayilan rota okunamazsa guvenli geri donustur.
        }
        List<String> addresses = localAddresses();
        return addresses.isEmpty() ? null : addresses.get(0);
    }
}
