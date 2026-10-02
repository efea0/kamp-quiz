package quiz.test;

import quiz.core.QuestionBank;
import quiz.core.QuizSet;
import quiz.core.QuizSetLoader;
import quiz.core.Scoreboard;
import quiz.model.Question;
import quiz.web.WebServer;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Web katmanı için kendi kendini test eden duman testi.
 *
 * Sunucuyu boş bir portta programatik başlatır; hiçbir dış test kütüphanesi
 * kullanmaz (projenin sıfır bağımlılık kuralı). Yönlendirmeler takip edilmez:
 * bir işlemin 303 dönmesi başlı başına bir denetimdir.
 */
public final class WebSmokeTest {

    /** 303 görmeyi beklediğimizde takip etmememiz gerekir. */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static String base;
    private static String cookie = "";

    private WebSmokeTest() {
    }

    /** Sunucuyu boş bir portta başlatır ve denetimleri çalıştırır. */
    public static void run() throws IOException, InterruptedException {
        startServer();
        try {
            testPublicPages();
            testFreeRoomFlow();
            testSyncRoomFlow();
            testCsvFormulaInjection();
            testExpiredSessionIsRemoved();
            testSecondRoundInSameRoom();
        } finally {
            WebServer.stopLastStarted();
            // Sonuç ekranı akışı skor dosyası oluşturur; test artığını bırakmasın.
            try {
                Files.deleteIfExists(Path.of("scores.txt"));
            } catch (IOException ignored) {
                // Silinemezse depo dışında kalır; test sonucunu etkilemez.
            }
        }
    }

    /** Boş bir port bulup sunucuyu o portta başlatır; dinlemeye başlamadan dönmez. */
    private static void startServer() throws IOException {
        List<Question> questions = QuestionBank.loadFromDirectory(Path.of("questions"));
        List<QuizSet> sets = QuizSetLoader.loadFromDirectory(Path.of("sets"));

        Path scores = Path.of("scores.txt");
        if (Files.exists(scores)) {
            try {
                Files.delete(scores);
            } catch (IOException ignored) {
                // Test başlamadan silinemezse sunucu kendi üzerine yazar; sorun değil.
            }
        }

        // Aynı anda kapalı-sonra-yeniden-açılan port bazen işletim sisteminde
        // hâlâ dolu görünür; bu yüzden başarılı bir bağlanma denenene kadar
        // farklı portlar denenir.
        IOException lastFailure = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            int port;
            try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
                port = probe.getLocalPort();
            }
            WebServer web = new WebServer(questions, sets,
                    Path.of("questions"), Path.of("sets"), new Scoreboard(scores), port);
            try {
                web.start();
                base = "http://127.0.0.1:" + port;
                waitForServer();
                return;
            } catch (IOException e) {
                lastFailure = e;
            }
        }
        throw new IOException("Test sunucusu hiçbir boş porta başlatılamadı.", lastFailure);
    }

    /** Port dinlemeye başlayana kadar bağlantıyı yeniler; sunucu geç açılırsa test kırılmaz. */
    private static void waitForServer() {
        for (int attempt = 0; attempt < 60; attempt++) {
            try {
                HttpResponse<Void> response = CLIENT.send(
                        HttpRequest.newBuilder(URI.create(base + "/style.css")).build(),
                        HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException | InterruptedException ignored) {
                // Sunucu henuz acilmamis; yeniden dene.
            }
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new IllegalStateException("Test sunucusu 6 saniyede dinlemeye başlamadı.");
    }

    // ------------------------------------------------------------- denetim 1-8

    private static void testPublicPages() throws IOException, InterruptedException {
        String home = get("/");

        check("Ana sayfa açılıyor ve test kartlarını listeliyor",
                getStatusCode("/") == 200 && home.contains("Hangi testi")
                        && home.contains("setcard"));
        check("Ana sayfada oda kodu kutusu var", home.contains("name=\"kod\""));

        String custom = get("/ayarla");
        check("Kendin ayarla sayfası açılıyor",
                getStatusCode("/ayarla") == 200 && custom.contains("Kendin ayarla")
                        && custom.contains("name=\"kategori\""));

        HttpResponse<String> css = raw("/style.css");
        check("Stil dosyası text/css olarak servis ediliyor",
                css.statusCode() == 200
                        && css.headers().firstValue("Content-Type").orElse("").startsWith("text/css")
                        && css.body().contains(":root"));

        check("Olmayan sayfa 404 dönüyor", getStatusCode("/yok-boyle") == 404);

        check("Lider tablosu açılıyor",
                getStatusCode("/tablo") == 200 && get("/tablo").contains("Lider Tablosu"));

        check("Soru üret sayfası anahtarsız açılıyor (kilitli mod)",
                getStatusCode("/uret") == 200);

        String setup = get("/kur");
        check("Oda kur sayfası akış ve sıra seçimi sunuyor",
                getStatusCode("/kur") == 200 && setup.contains("name=\"mod\"")
                        && setup.contains("name=\"sira\""));
    }

    // ------------------------------------------------------------ denetim 9-22

    private static void testFreeRoomFlow() throws IOException, InterruptedException {
        HttpResponse<String> created = post("/kur",
                "mod=serbest&sira=paylasik&set=" + urlEncode(firstSetName()));
        String location = created.headers().firstValue("Location").orElse("");
        check("Oda kurulumu 303 ile yeni odaya yönlendiriyor",
                created.statusCode() == 303 && location.contains("kod="));

        String roomCode = roomCodeOf(location);
        String panel = get("/oda?kod=" + roomCode);
        check("Oda paneli kodu ve katılım QR'sini gösteriyor",
                getStatusCode("/oda?kod=" + roomCode) == 200
                        && panel.contains("class=\"code\"") && panel.contains("<svg"));

        String screen = get("/ekran?kod=" + roomCode);
        check("Projeksiyon ekranı canlı sıralama ve otomatik yenileme içeriyor",
                getStatusCode("/ekran?kod=" + roomCode) == 200
                        && screen.contains("Canlı Sıralama")
                        && screen.contains("http-equiv=\"refresh\""));

        HttpResponse<String> joined = post("/katil", "kod=" + roomCode + "&isim=Deneyci");
        cookie = joined.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("qsid="))
                .findFirst()
                .map(value -> value.substring(0, value.indexOf(';')))
                .orElse("");
        check("Odaya katılım 303 dönüyor ve oturum çerezi veriyor",
                joined.statusCode() == 303 && !cookie.isEmpty());

        check("Aynı isimle ikinci katılım 409 dönüyor",
                postFresh("/katil", "kod=" + roomCode + "&isim=Deneyci").statusCode() == 409);

        check("Olmayan oda kodu 404 dönüyor",
                post("/katil", "kod=9999&isim=Yalnız").statusCode() == 404);

        String quiz = get("/quiz");
        check("Soru ekranı ilk soruyu, cevap formunu ve sayacı gösteriyor",
                getStatusCode("/quiz") == 200 && quiz.contains("Soru 1 / ")
                        && quiz.contains("name=\"cevap\"") && quiz.contains("id=\"saat\""));

        check("Cevap gönderimi 303 ile cevap ekranına yönlendiriyor",
                post("/cevap", "cevap=0").statusCode() == 303);

        String afterAnswer = get("/quiz");
        check("Cevap ekranı karne ve devam bağlantısı içeriyor",
                afterAnswer.contains("class=\"verdict ") && afterAnswer.contains("href=\"/devam\""));

        check("Devam 303 dönüyor", getStatusCode("/devam") == 303);

        String result = get("/sonuc");
        check("Sonuç ekranı büyük skoru gösteriyor",
                getStatusCode("/sonuc") == 200 && result.contains("class=\"bigscore\""));

        check("Sonuç ekranında tekrar veya tablo bağlantısı var",
                result.contains("/tekrar") || result.contains("/tablo"));

        check("Yanlış raporu açılıyor",
                getStatusCode("/rapor?kod=" + roomCode) == 200
                        && get("/rapor?kod=" + roomCode).contains("Yanlış raporu"));

        check("Tekrar turu 303 dönüyor", getStatusCode("/tekrar") == 303);
    }

    // ------------------------------------------------------------ denetim 23-28

    private static void testSyncRoomFlow() throws IOException, InterruptedException {
        HttpResponse<String> created = post("/kur",
                "mod=senkron&sira=paylasik&set=" + urlEncode(firstSetName()));
        String roomCode = roomCodeOf(created.headers().firstValue("Location").orElse(""));

        HttpResponse<String> joined = post("/katil", "kod=" + roomCode + "&isim=Sinif");
        cookie = joined.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("qsid="))
                .findFirst()
                .map(value -> value.substring(0, value.indexOf(';')))
                .orElse("");
        String lobby = get("/quiz");
        check("Senkron odaya katılan lobide hazır oluyor",
                lobby.contains("Hazır ol") || lobby.contains("odadasın"));

        String panel = get("/oda?kod=" + roomCode);
        check("Senkron oda panelinde Başlat düğmesi var",
                panel.contains("value=\"basla\""));

        post("/oda?kod=" + roomCode, "islem=basla");
        check("Başlat sonrası oyuncu ilk soruyu görüyor", get("/quiz").contains("Soru 1 / "));

        post("/cevap", "cevap=0");
        String waiting = get("/quiz");
        check("Cevap verince bekleme ekranı çıkıyor",
                waiting.contains("Cevabın alındı"));

        post("/oda?kod=" + roomCode, "islem=goster");
        String review = get("/quiz");
        check("Cevap açıklandığında karne ve sıralama görünüyor",
                review.contains("class=\"verdict ") && review.contains("class=\"rank\""));

        post("/oda?kod=" + roomCode, "islem=sonraki");
        check("Sonraki soruya geçiliyor", get("/quiz").contains("Soru 2 / "));
    }

    // ---------------------------------------------------------------- yardımcı

    /**
     * Test bittikten sonra hoca AYNI odada ikinci turu baslatabilmeli.
     *
     * Onceden bu mumkun degildi: oda BITTI fazinda kalip panelde sadece
     * "test bitti" yaziyordu. Hoca ikinci tur icin /kur'a donup yeni oda
     * kurmak zorundaydi; o eski oyuncular yeni odaya girmedigi icin sinif
     * yariya kadar bos kaliyordu.
     *
     * Test: oyunu bitir -> panelde "tekrar" butonu var mi? -> baslat ->
     * faz LOBI'ye donuyor mu -> "basla" ile oyun yeniden basliyor mu ->
     * oyuncu YENI soru aliyor mu?
     */
    /**
     * Test bittikten sonra hoca AYNI odada ikinci turu baslatabilmeli.
     *
     * Onceden mumkun degildi. Iki ayri sebep vardi:
     *
     *  1) Serbest odada faz hicbir zaman BITTI olmuyordu; oyuncular kendi
     *     hizinda ilerler, "herkes bitti" ani odanin index'inde degil
     *     oyuncularin Quiz'inde beliriyor. Panel sadece index'e baktigi icin
     *     oyun bitince de "Cevabi goster" diyordu. displayPhase() bunu
     *     everyoneFinished() ile birlestirir.
     *
     *  2) hostControls() serbest odada tamamen bos donuyordu. Artik oyun
     *     bitince "Ayni odada tekrar oyna" butonu gosterir.
     *
     * Test izole bir oturum kullanir: paylasilan "cookie" degiskeni onceki
     * testlerden kalan oyunculari odada tutuyor ve everyoneFinished()'i
     * false birakiyordu.
     */
    private static void testSecondRoundInSameRoom() throws IOException, InterruptedException {
        String oncekiCookie = cookie;
        cookie = "";

        String oda = roomCodeOf(post("/kur", "mod=serbest&sira=paylasik&set="
                + urlEncode(firstSetName()))
                .headers().firstValue("Location").orElse(""));
        check("Ikinci tur icin oda kuruldu", !oda.isEmpty());

        HttpResponse<String> joined = post("/katil", "kod=" + oda + "&isim=TurOyuncu");
        cookie = joined.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("qsid="))
                .findFirst()
                .map(value -> value.substring(0, value.indexOf(';')))
                .orElse("");
        check("Ikinci tur icin oyuncu katildi ve oturum acildi", !cookie.isEmpty());

        // Turlari bitir. "Hizli Tur" 10 soru; 30 deneme hepsini kapsar.
        for (int i = 0; i < 30; i++) {
            post("/cevap", "kod=" + oda + "&cevap=0");
            post("/devam", "kod=" + oda);
        }

        // Oyuncu sonuc ekranina ulasin (odanin BITTI sayilmasi icin sart:
        // everyoneFinished() TUM oyuncularin quiz'inin bitmesini bekler).
        // /quiz bittikten sonra 303 ile /sonuc'a yonlendirir; opener
        // redirect'i takip etmedigi icin dogrudan /sonuc okunur.
        String oyuncu = get("/sonuc");
        check("Birinci tur bitti, oyuncu sonuc ekraninda",
                oyuncu.contains("Sonuç") || oyuncu.contains("toplam puan"));

        String panel = get("/oda?kod=" + oda);
        check("Panelde 'Aynı odada tekrar oyna' butonu var",
                panel.contains("Aynı odada tekrar oyna"));

        // Ikinci turu baslat
        post("/oda?kod=" + oda, "islem=tekrar");

        String soru = get("/quiz");
        check("Ikinci turda oyuncu yeni soru aliyor",
                soru.contains("Soru 1") && soru.contains("name=\"cevap\""));

        // Skor sifirlanmis olmali: yeni Quiz, eski puan tasinmaz.
        check("Ikinci turda skor sifirlandi", !soru.contains("toplam puan"));

        // Ikinci tur da oynanabilmeli
        post("/cevap", "kod=" + oda + "&cevap=1");
        String cevapli = get("/quiz");
        check("Ikinci turda cevap verilebiliyor",
                cevapli.contains("Devam") || cevapli.contains("puan"));

        cookie = oncekiCookie;
    }

    /**
     * Sure dolmus oturum ve odalar gercekten siliniyor mu? (issue #3/#9)
     *
     * Gercek saati beklemek testi yavaslatirdi; bu yuzden cleanup'in zaman
     * parametresi var ve test "simdi" degerini kendisi veriyor. Boylece
     * davranis deterministik: 3 saat sonra oturum silinir, yarim saat sonra
     * SILINMEZ.
     */
    private static void testExpiredSessionIsRemoved() throws IOException, InterruptedException {
        if (WebServer.lastSessionCount() < 0) {
            check("Temizlik testi icin sunucu baglami alindi", false);
            return;
        }

        long simdi = System.currentTimeMillis();

        // 1) Yeni oturum: su an gorulmus sayilmali
        String savedCookie = cookie;
        cookie = "";
        String room = roomCodeOf(post("/kur", "set="
                + URLEncoder.encode(firstSetName(), StandardCharsets.UTF_8)
                + "&mod=serbest&sira=paylasik")
                .headers().firstValue("Location").orElse(""));
        post("/katil", "kod=" + room + "&isim=Temizlik");
        cookie = savedCookie;
        check("Temizlik testi icin oyuncu katildi", WebServer.lastSessionCount() > 0);

        int odaSayisi = WebServer.lastRoomCount();

        // 2) Hemen temizlik: hicbiri silinmemeli
        int silinen = WebServer.runCleanup(simdi);
        check("Taze oturum ve oda temizlikte korunur", silinen == 0);

        // 3) 3 saati askin zaman: oturum silinmeli
        long ucSaatSonra = simdi + WebServer.sessionTimeoutMillis() + 1000;
        int sonra = WebServer.runCleanup(ucSaatSonra);
        check("Sure dolmus oturum silinir", sonra >= 1);
        check("Oturumlar haritasi bosaldi", WebServer.lastSessionCount() == 0);

        // 4) Oda bos oldugu icin de silinmis olmali
        check("Bos oda da toplandi", WebServer.lastRoomCount() < odaSayisi);
    }

    /**
     * Oyuncu adi CSV'ye dogrudan yaziliyordu; "=..." ile baslayan bir isim
     * Excel/LibreOffice'da FORMUL olarak calistirilir (formul enjeksiyonu).
     * csvField artik bu karakterleri tirnakli ve tek tirnak onekiyle yaziyor.
     * Burada o davranisi kilitliyoruz: bir sonraki degisiklikte geri gelirse
     * test kirmizi verir.
     */
    private static void testCsvFormulaInjection() throws IOException, InterruptedException {
        String name = "=cmd|' /c calc'!A1";
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
        String location = post("/kur", "set="
                + URLEncoder.encode(firstSetName(), StandardCharsets.UTF_8)
                + "&mod=serbest&sira=paylasik")
                .headers().firstValue("Location").orElse("");
        String room = roomCodeOf(location);
        check("CSV testi icin oda kuruldu", !room.isEmpty());

        String savedCookie = cookie;
        cookie = "";
        post("/katil", "kod=" + room + "&isim=" + encoded);
        cookie = savedCookie;

        String csv = get("/disaktar/oda?kod=" + room);
        check("CSV formulu tek tirnak onekiyle korundu", csv.contains("'="));
        check("Hucre tirnak icinde yazildi", csv.contains("\""));
    }

    private static String firstSetName() {
        List<QuizSet> sets = QuizSetLoader.loadFromDirectory(Path.of("sets"));
        return sets.isEmpty() ? "" : sets.get(0).getName();
    }

    private static String roomCodeOf(String location) {
        int at = location.indexOf("kod=");
        return at < 0 ? "" : location.substring(at + 4).split("&")[0];
    }

    private static String get(String path) throws IOException, InterruptedException {
        return raw(path).body();
    }

    private static int getStatusCode(String path) throws IOException, InterruptedException {
        return raw(path).statusCode();
    }

    private static HttpResponse<String> raw(String path) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path));
        if (!cookie.isEmpty()) {
            builder.header("Cookie", cookie);
        }
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> post(String path, String form)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8));
        if (!cookie.isEmpty()) {
            builder.header("Cookie", cookie);
        }
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** Çerez göndermeden POST yapar; yeni tarayıcıyı taklit eder. */
    private static HttpResponse<String> postFresh(String path, String form)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** SelfTest ile aynı sayaçları kullanır; çıktı biçimi tek yerde kalır. */
    static void check(String what, boolean condition) {
        SelfTest.check(what, condition);
    }
}
