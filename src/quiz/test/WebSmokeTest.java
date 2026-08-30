package quiz.test;

import quiz.core.QuestionBank;
import quiz.core.QuizSet;
import quiz.core.QuizSetLoader;
import quiz.core.Scoreboard;
import quiz.web.WebServer;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URLEncoder;

/** Web katmaninin ana akislari icin bagimliliksiz HTTP duman testi. */
public final class WebSmokeTest {

    private static final Path QUESTIONS_DIR = Path.of("questions");
    private static final Path SETS_DIR = Path.of("sets");
    private static final Path SCORES_FILE = Path.of("scores.txt");
    private static final Pattern ROOM_CODE = Pattern.compile("(?:[?&])kod=([0-9]{4})(?:&|$)");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(Redirect.NEVER)
            .build();

    private WebSmokeTest() {
    }

    /** Sunucuyu bos portta acar, 28 HTTP kontrolunu calistirir ve kapatir. */
    public static void run() throws IOException, InterruptedException {
        List<quiz.model.Question> questions = QuestionBank.loadFromDirectory(QUESTIONS_DIR);
        List<QuizSet> sets = QuizSetLoader.loadFromDirectory(SETS_DIR);
        WebServer server = new WebServer(questions, sets, QUESTIONS_DIR, SETS_DIR,
                new Scoreboard(SCORES_FILE), 0);

        try {
            server.startQuietly();
            URI base = URI.create("http://127.0.0.1:" + server.getBoundPort() + "/");
            SmokeState state = new SmokeState();
            runChecks(base, state);
        } finally {
            server.stop();
            Files.deleteIfExists(SCORES_FILE);
        }
    }

    private static void runChecks(URI base, SmokeState state) {
        SelfTest.check("Web GET / temel sayfayi veriyor", passes(() -> {
            HttpResponse<String> response = get(base, "/", null);
            return response.statusCode() == 200
                    && response.body().contains("Hangi testi")
                    && response.body().contains("class=\"setcard\"");
        }));

        SelfTest.check("Web ana sayfada oda kodu kutusu var", passes(() -> {
            HttpResponse<String> response = get(base, "/", null);
            return response.statusCode() == 200 && response.body().contains("name=\"kod\"");
        }));

        SelfTest.check("Web /ayarla kendi ayarlarini gosteriyor", passes(() -> {
            HttpResponse<String> response = get(base, "/ayarla", null);
            return response.statusCode() == 200
                    && response.body().contains("Kendin ayarla")
                    && response.body().contains("name=\"kategori\"");
        }));

        SelfTest.check("Web CSS dogru icerik turu ve kok degiskenleriyle geliyor", passes(() -> {
            HttpResponse<String> response = get(base, "/style.css", null);
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            return response.statusCode() == 200
                    && contentType.toLowerCase().startsWith("text/css")
                    && response.body().contains(":root");
        }));

        SelfTest.check("Web olmayan sayfada 404 donuyor", passes(() -> {
            return get(base, "/yok-boyle", null).statusCode() == 404;
        }));

        SelfTest.check("Web lider tablosunu gosteriyor", passes(() -> {
            HttpResponse<String> response = get(base, "/tablo", null);
            return response.statusCode() == 200 && response.body().contains("<h1>Lider Tablosu</h1>");
        }));

        SelfTest.check("Web anahtar yokken soru ureticiyi kapali gosteriyor", passes(() -> {
            HttpResponse<String> response = get(base, "/uret", null);
            return response.statusCode() == 200 && response.body().contains("AI özelliği kapalı");
        }));

        SelfTest.check("Web oda kurulum alanlarini gosteriyor", passes(() -> {
            HttpResponse<String> response = get(base, "/kur", null);
            return response.statusCode() == 200
                    && response.body().contains("name=\"mod\"")
                    && response.body().contains("name=\"sira\"");
        }));

        SelfTest.check("Web serbest oda POST ile 303 ve kod donuyor", passes(() -> {
            HttpResponse<String> response = post(base, "/kur",
                    form("mod", "serbest", "sira", "paylasik", "set", "Hızlı Tur"), state.host);
            state.roomCode = roomCode(response);
            return response.statusCode() == 303 && isRoomCode(state.roomCode)
                    && state.host.value().startsWith("qhost=")
                    && response.headers().firstValue("Set-Cookie").orElse("").contains("HttpOnly")
                    && response.headers().firstValue("Location").orElse("").contains("kod=");
        }));

        SelfTest.check("Web oda sayfasi kodu ve QR SVG'sini gosteriyor", passes(() -> {
            HttpResponse<String> response = get(base, "/oda?kod=" + state.roomCode, state.host);
            return response.statusCode() == 200
                    && response.body().contains("class=\"code\"")
                    && response.body().contains("<svg");
        }));

        SelfTest.check("Web oda sonuclarini CSV olarak indiriyor", passes(() -> {
            HttpResponse<String> response = get(base, "/disaktar/oda?kod=" + state.roomCode, state.host);
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            return response.statusCode() == 200
                    && contentType.toLowerCase().startsWith("text/csv")
                    && response.body().contains("sira,oyuncu,puan");
        }));

        SelfTest.check("Web projeksiyon sayfasi canli siralamayi yeniliyor", passes(() -> {
            HttpResponse<String> response = get(base, "/ekran?kod=" + state.roomCode, null);
            return response.statusCode() == 200
                    && response.body().contains("Canlı Sıralama")
                    && response.body().contains("http-equiv=\"refresh\"");
        }));

        SelfTest.check("Web katilimda 303 ve qsid cerezini veriyor", passes(() -> {
            HttpResponse<String> response = post(base, "/katil",
                    form("kod", state.roomCode, "isim", "Deneyci"), state.player);
            return response.statusCode() == 303 && state.player.hasSessionCookie();
        }));

        SelfTest.check("Web ayni isimle ikinci katilimi 409 ile reddediyor", passes(() -> {
            HttpResponse<String> response = post(base, "/katil",
                    form("kod", state.roomCode, "isim", "Deneyci"), null);
            return response.statusCode() == 409;
        }));

        SelfTest.check("Web olmayan oda koduna 404 donuyor", passes(() -> {
            HttpResponse<String> response = post(base, "/katil",
                    form("kod", "9999", "isim", "Baska"), null);
            return response.statusCode() == 404;
        }));

        SelfTest.check("Web oyuncuya ilk soruyu ve cevap formunu gosteriyor", passes(() -> {
            HttpResponse<String> response = get(base, "/quiz", state.player);
            return response.statusCode() == 200
                    && response.body().contains("Soru 1 / ")
                    && response.body().contains("name=\"cevap\"")
                    && response.body().contains("id=\"saat\"");
        }));

        SelfTest.check("Web cevap POST istegini 303 ile yonlendiriyor", passes(() -> {
            HttpResponse<String> response = post(base, "/cevap", form("cevap", "0"), state.player);
            return response.statusCode() == 303;
        }));

        SelfTest.check("Web cevap sonrasi verdict ve devam baglantisini gosteriyor", passes(() -> {
            HttpResponse<String> response = get(base, "/quiz", state.player);
            return response.statusCode() == 200
                    && response.body().contains("class=\"verdict")
                    && response.body().contains("href=\"/devam\"");
        }));

        SelfTest.check("Web devam akisini 303 ile ilerletiyor", passes(() -> {
            return get(base, "/devam", state.player).statusCode() == 303;
        }));

        SelfTest.check("Web sonuc sayfasinda buyuk skor var", passes(() -> {
            HttpResponse<String> response = get(base, "/sonuc", state.player);
            return response.statusCode() == 200 && response.body().contains("class=\"bigscore\"");
        }));

        SelfTest.check("Web sonuc sayfasi tekrar veya lider tablosu baglantisi veriyor", passes(() -> {
            HttpResponse<String> response = get(base, "/sonuc", state.player);
            return response.body().contains("href=\"/tekrar\"")
                    || response.body().contains("href=\"/tablo\"");
        }));

        SelfTest.check("Web yanlis raporu sayfasini gosteriyor", passes(() -> {
            HttpResponse<String> response = get(base, "/rapor?kod=" + state.roomCode, state.host);
            return response.statusCode() == 200 && response.body().contains("Yanlış raporu");
        }));

        SelfTest.check("Web soru raporunu CSV olarak indiriyor", passes(() -> {
            HttpResponse<String> response = get(base, "/disaktar/sorular?kod=" + state.roomCode, state.host);
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            return response.statusCode() == 200
                    && contentType.toLowerCase().startsWith("text/csv")
                    && response.body().contains("soru,kategori,dogru_cevap");
        }));

        SelfTest.check("Web tekrar akisini 303 ile baslatiyor", passes(() -> {
            return get(base, "/tekrar", state.player).statusCode() == 303;
        }));

        SelfTest.check("Web senkron lobiye katilani Hazır ol ekraniyla karsiliyor", passes(() -> {
            HttpResponse<String> created = post(base, "/kur",
                    form("mod", "senkron", "sira", "paylasik", "set", "Hızlı Tur"), state.syncHost);
            state.syncRoomCode = roomCode(created);
            HttpResponse<String> joined = post(base, "/katil",
                    form("kod", state.syncRoomCode, "isim", "Senkroncu"), state.syncPlayer);
            HttpResponse<String> lobby = get(base, "/quiz", state.syncPlayer);
            return created.statusCode() == 303 && joined.statusCode() == 303
                    && lobby.statusCode() == 200 && lobby.body().contains("Hazır ol");
        }));

        SelfTest.check("Web senkron oda panelinde baslat dugmesi var", passes(() -> {
            HttpResponse<String> response = get(base, "/oda?kod=" + state.syncRoomCode, state.syncHost);
            return response.statusCode() == 200 && response.body().contains("value=\"basla\"");
        }));

        SelfTest.check("Web senkron paneli host cerezini olmadan acilmiyor", passes(() -> {
            HttpResponse<String> response = get(base, "/oda?kod=" + state.syncRoomCode, null);
            return response.statusCode() == 403 && response.body().contains("Yönetici erişimi");
        }));

        SelfTest.check("Web senkron baslatma host cerezini olmadan calismiyor", passes(() -> {
            HttpResponse<String> blocked = post(base, "/oda?kod=" + state.syncRoomCode,
                    form("islem", "basla"), null);
            HttpResponse<String> lobby = get(base, "/quiz", state.syncPlayer);
            return blocked.statusCode() == 403 && lobby.statusCode() == 200
                    && lobby.body().contains("Hazır ol");
        }));

        SelfTest.check("Web senkron baslatilinca oyuncuya ilk soru geliyor", passes(() -> {
            HttpResponse<String> started = post(base, "/oda?kod=" + state.syncRoomCode,
                    form("islem", "basla"), state.syncHost);
            HttpResponse<String> question = get(base, "/quiz", state.syncPlayer);
            return started.statusCode() == 303 && question.statusCode() == 200
                    && question.body().contains("Soru 1 / ");
        }));

        SelfTest.check("Web senkron cevap sonrasi oyuncu bekleme mesajini goruyor", passes(() -> {
            HttpResponse<String> answered = post(base, "/cevap", form("cevap", "0"), state.syncPlayer);
            HttpResponse<String> waiting = get(base, "/quiz", state.syncPlayer);
            return answered.statusCode() == 303 && waiting.statusCode() == 200
                    && waiting.body().contains("Cevabın alındı");
        }));

        SelfTest.check("Web senkron cevap gosterilince verdict ve siralama geliyor", passes(() -> {
            HttpResponse<String> revealed = post(base, "/oda?kod=" + state.syncRoomCode,
                    form("islem", "goster"), state.syncHost);
            HttpResponse<String> review = get(base, "/quiz", state.syncPlayer);
            return revealed.statusCode() == 303 && review.statusCode() == 200
                    && review.body().contains("class=\"verdict")
                    && review.body().contains("class=\"rank");
        }));

        SelfTest.check("Web senkron sonraki isleminden sonra ikinci soru geliyor", passes(() -> {
            HttpResponse<String> next = post(base, "/oda?kod=" + state.syncRoomCode,
                    form("islem", "sonraki"), state.syncHost);
            HttpResponse<String> question = get(base, "/quiz", state.syncPlayer);
            return next.statusCode() == 303 && question.statusCode() == 200
                    && question.body().contains("Soru 2 / ");
        }));
    }

    private static HttpResponse<String> get(URI base, String path, CookieJar cookies)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path)).GET();
        addCookie(request, cookies);
        return send(request.build(), cookies);
    }

    private static HttpResponse<String> post(URI base, String path, String body, CookieJar cookies)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        addCookie(request, cookies);
        return send(request.build(), cookies);
    }

    private static HttpResponse<String> send(HttpRequest request, CookieJar cookies)
            throws IOException, InterruptedException {
        for (int attempt = 0; ; attempt++) {
            try {
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (cookies != null) {
                    cookies.accept(response.headers());
                }
                return response;
            } catch (ConnectException e) {
                if (attempt >= 20) {
                    throw e;
                }
                Thread.sleep(50L);
            }
        }
    }

    private static void addCookie(HttpRequest.Builder request, CookieJar cookies) {
        if (cookies != null && !cookies.value().isEmpty()) {
            request.header("Cookie", cookies.value());
        }
    }

    private static String form(String... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("Form anahtar-deger cifti bekliyor.");
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i += 2) {
            if (result.length() > 0) {
                result.append('&');
            }
            result.append(URLEncoder.encode(values[i], StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(values[i + 1], StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    private static String roomCode(HttpResponse<String> response) {
        String location = response.headers().firstValue("Location").orElse("");
        Matcher matcher = ROOM_CODE.matcher(location);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static boolean isRoomCode(String value) {
        return value != null && value.matches("[0-9]{4}");
    }

    private static boolean passes(CheckedAssertion assertion) {
        try {
            return assertion.test();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @FunctionalInterface
    private interface CheckedAssertion {
        boolean test() throws Exception;
    }

    private static final class CookieJar {
        private String value = "";

        String value() {
            return value;
        }

        boolean hasSessionCookie() {
            return value.startsWith("qsid=") && value.length() > "qsid=".length();
        }

        void accept(HttpHeaders headers) {
            headers.firstValue("Set-Cookie").ifPresent(header -> {
                int semicolon = header.indexOf(';');
                value = semicolon >= 0 ? header.substring(0, semicolon) : header;
            });
        }
    }

    private static final class SmokeState {
        private final CookieJar host = new CookieJar();
        private final CookieJar player = new CookieJar();
        private final CookieJar syncHost = new CookieJar();
        private final CookieJar syncPlayer = new CookieJar();
        private String roomCode = "";
        private String syncRoomCode = "";
    }
}
