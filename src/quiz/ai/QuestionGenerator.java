package quiz.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Google Gemini ile soru paketi uretir ve duzenler.
 *
 * API ANAHTARI KODA YAZILMAZ. Ortam degiskeninden okunur:
 *
 *     export GEMINI_API_KEY="..."        # zorunlu
 *     export GEMINI_MODEL="..."          # istege bagli
 *     export GEMINI_BASE_URL="..."       # istege bagli (test icin)
 *
 * Anahtar yoksa sinif "kapali" durumda kalir; uygulama calismaya devam eder,
 * sadece uretim sayfasi devre disi gorunur.
 */
public class QuestionGenerator {

    private static final String DEFAULT_BASE = "https://generativelanguage.googleapis.com";
    private static final String DEFAULT_MODEL = "gemini-2.5-flash";

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient http;

    public QuestionGenerator() {
        this(System.getenv("GEMINI_API_KEY"),
             envOr("GEMINI_BASE_URL", DEFAULT_BASE),
             envOr("GEMINI_MODEL", DEFAULT_MODEL));
    }

    public QuestionGenerator(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /** Anahtar tanimli mi? Degilse uretim ozelligi kapali gosterilir. */
    public boolean isEnabled() {
        return !apiKey.isEmpty();
    }

    public String getModel() {
        return model;
    }

    /** Verilen konuda yeni bir soru paketi metni uretir. */
    public String generate(String topic, int count, String level) throws IOException {
        String prompt = """
                Sen bir bilgi yarismasi soru yazarisin. Turkce, %s seviyesinde,
                "%s" konusunda TAM %d adet coktan secmeli soru yaz.

                CIKTI BICIMI cok onemli. Sadece asagidaki bicimde satirlar yaz,
                baska hicbir sey yazma (giris cumlesi, numaralandirma, markdown, kod blogu YOK):

                Soru metni | sik1 | sik2 | sik3 | sik4 | dogruNo
                > Dogru cevabin kisa nedeni.

                KURALLAR:
                - Ayirici karakter dikey cizgi (|). Soru metninde ve siklarda dikey cizgi KULLANMA.
                - Her soruda tam 4 sik olsun.
                - dogruNo 1 ile 4 arasinda bir sayidir; 1 = ilk sik.
                - Her sorunun hemen altina '>' ile baslayan tek satirlik bir aciklama yaz.
                - Aciklama nedeni soylesin ve sik karistirilan sikki ayirt etsin.
                - Dogru cevaplar hep ayni sirada olmasin, dagitilsin.
                - Bilgiler dogru ve tartismasiz olsun; tarihsel veya teknik hata yapma.
                - Her soru tek satirda olsun, satir sonu ekleme.
                """.formatted(level, topic, count);

        return callModel(prompt);
    }

    /** Var olan taslagi verilen talimata gore yeniden yazar. */
    public String revise(String draft, String instruction) throws IOException {
        String prompt = """
                Asagida bir bilgi yarismasi soru paketi var. Kullanicinin istegine gore
                bu paketi duzenle ve TAM AYNI BICIMDE geri ver.

                KULLANICININ ISTEGI:
                %s

                BICIM KURALLARI (degistirme):
                Soru metni | sik1 | sik2 | sik3 | sik4 | dogruNo
                > Dogru cevabin kisa nedeni.

                - Sadece duzenlenmis paketi yaz; aciklama, giris cumlesi, markdown, kod blogu YOK.
                - Dikey cizgi sadece ayirici olarak kullanilir.
                - dogruNo 1 ile 4 arasindadir.

                MEVCUT PAKET:
                %s
                """.formatted(instruction, draft);

        return callModel(prompt);
    }

    /** Modele istek gonderir ve metin cevabini dondurur. */
    private String callModel(String prompt) throws IOException {
        if (!isEnabled()) {
            throw new IOException("GEMINI_API_KEY tanımlı değil.");
        }

        String body = """
                {"contents":[{"parts":[{"text":"%s"}]}],
                 "generationConfig":{"temperature":0.7,"maxOutputTokens":4096}}
                """.formatted(Json.escape(prompt));

        // Anahtar URL'ye DEGIL basliga konur. URL'ye konsaydi hata mesajlarina,
        // sunucu kayitlarina ve vekil sunucu gunluklerine sizabilirdi.
        URI uri = URI.create(baseUrl + "/v1beta/models/" + model + ":generateContent");

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("İstek yarıda kesildi.", e);
        } catch (IOException e) {
            // Ag hatasi mesajinda adres gecebilir; yine de anahtari maskeliyoruz.
            throw new IOException("Modele ulaşılamadı: " + maskele(e.getMessage()));
        }

        if (response.statusCode() != 200) {
            List<String> messages = Json.valuesOf(response.body(), "message");
            String detail = messages.isEmpty() ? kisalt(response.body()) : messages.get(0);
            throw new IOException("Model hatası (HTTP " + response.statusCode() + "): " + maskele(detail));
        }

        // Cevap parcalar halinde gelebilir; hepsini birlestir.
        List<String> parts = Json.valuesOf(response.body(), "text");
        if (parts.isEmpty()) {
            throw new IOException("Model boş cevap döndürdü.");
        }

        return temizle(String.join("", parts));
    }

    /** Model bazen kod blogu isaretleri ekler; onlari ayikla. */
    private static String temizle(String text) {
        String result = text.strip();
        if (result.startsWith("```")) {
            int firstBreak = result.indexOf('\n');
            if (firstBreak > 0) {
                result = result.substring(firstBreak + 1);
            }
            int fenceEnd = result.lastIndexOf("```");
            if (fenceEnd >= 0) {
                result = result.substring(0, fenceEnd);
            }
        }
        return result.strip();
    }

    /** Bir metinde anahtar gecerse gizler. Son savunma hatti. */
    private String maskele(String text) {
        if (text == null) {
            return "bilinmeyen hata";
        }
        return apiKey.isEmpty() ? text : text.replace(apiKey, "***");
    }

    private static String kisalt(String text) {
        String flat = text.replace('\n', ' ').strip();
        return flat.length() > 240 ? flat.substring(0, 240) + "..." : flat;
    }
}
