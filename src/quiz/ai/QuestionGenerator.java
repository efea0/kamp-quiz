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
 * Iki saglayici desteklenir:
 *
 *   Google Gemini:
 *     export GEMINI_API_KEY="..."
 *     export GEMINI_MODEL="gemini-2.5-flash"        # istege bagli
 *
 *   OpenRouter (ve OpenAI uyumlu her servis):
 *     export OPENROUTER_API_KEY="..."
 *     export OPENROUTER_MODEL="saglayici/model"     # istege bagli
 *
 * API ANAHTARI KODA YAZILMAZ, ortam degiskeninden okunur. Ikisi de tanimliysa
 * OpenRouter tercih edilir; AI_PROVIDER=gemini ile bu degistirilebilir.
 *
 * Anahtar yoksa sinif "kapali" durumda kalir; uygulama calismaya devam eder,
 * sadece uretim sayfasi devre disi gorunur.
 */
public class QuestionGenerator {

    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com";
    private static final String GEMINI_MODEL = "gemini-2.5-flash";
    private static final String OPENROUTER_BASE = "https://openrouter.ai/api/v1";
    private static final String OPENROUTER_MODEL = "deepseek/deepseek-chat";

    /** Hangi servise konusuyoruz. */
    public enum Provider { GEMINI, OPENROUTER }

    private final Provider provider;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient http;

    public QuestionGenerator() {
        this(resolveProvider());
    }

    private QuestionGenerator(Provider provider) {
        this.provider = provider;
        if (provider == Provider.OPENROUTER) {
            this.apiKey = trim(System.getenv("OPENROUTER_API_KEY"));
            this.baseUrl = stripSlash(envOr("OPENROUTER_BASE_URL", OPENROUTER_BASE));
            this.model = envOr("OPENROUTER_MODEL", OPENROUTER_MODEL);
        } else {
            this.apiKey = trim(System.getenv("GEMINI_API_KEY"));
            this.baseUrl = stripSlash(envOr("GEMINI_BASE_URL", GEMINI_BASE));
            this.model = envOr("GEMINI_MODEL", GEMINI_MODEL);
        }
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** Test icin: saglayici, anahtar, adres ve modeli dogrudan verir. */
    QuestionGenerator(Provider provider, String apiKey, String baseUrl, String model) {
        this.provider = provider;
        this.apiKey = trim(apiKey);
        this.baseUrl = stripSlash(baseUrl);
        this.model = model;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    /**
     * Hangi saglayici kullanilacak?
     * AI_PROVIDER acikca yazilmissa o; yazilmamissa hangi anahtar tanimliysa o.
     */
    private static Provider resolveProvider() {
        String explicit = trim(System.getenv("AI_PROVIDER")).toLowerCase();
        if (explicit.startsWith("gemini")) {
            return Provider.GEMINI;
        }
        if (explicit.startsWith("openrouter") || explicit.startsWith("openai")) {
            return Provider.OPENROUTER;
        }
        return trim(System.getenv("OPENROUTER_API_KEY")).isEmpty()
                ? Provider.GEMINI : Provider.OPENROUTER;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
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

    public Provider getProvider() {
        return provider;
    }

    /** Ekranda gosterilecek kisa etiket. */
    public String describe() {
        return (provider == Provider.OPENROUTER ? "OpenRouter" : "Gemini") + " · " + model;
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
            throw new IOException("API anahtarı tanımlı değil.");
        }

        String escaped = Json.escape(prompt);
        String body;
        HttpRequest.Builder builder;

        if (provider == Provider.OPENROUTER) {
            // OpenAI uyumlu bicim; OpenRouter disindaki cogu servis de bunu kullanir.
            body = """
                    {"model":"%s","messages":[{"role":"user","content":"%s"}],"temperature":0.7}
                    """.formatted(Json.escape(model), escaped);
            builder = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey);
        } else {
            body = """
                    {"contents":[{"parts":[{"text":"%s"}]}],
                     "generationConfig":{"temperature":0.7,"maxOutputTokens":4096}}
                    """.formatted(escaped);
            builder = HttpRequest.newBuilder(
                            URI.create(baseUrl + "/v1beta/models/" + model + ":generateContent"))
                    .header("x-goog-api-key", apiKey);
        }

        // Anahtar HER IKI SAGLAYICIDA DA basliga konur, URL'ye degil.
        // URL'ye konsaydi hata mesajlarina ve vekil sunucu gunluklerine sizabilirdi.
        HttpRequest request = builder
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json; charset=UTF-8")
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

        // Gemini metni "text", OpenAI uyumlu servisler "content" altinda dondurur.
        String key = provider == Provider.OPENROUTER ? "content" : "text";
        List<String> parts = Json.valuesOf(response.body(), key);
        parts.removeIf(String::isBlank);
        if (parts.isEmpty()) {
            throw new IOException("Model boş cevap döndürdü.");
        }

        // OpenAI bicimi tek parca dondurur; Gemini parcalara bolebilir.
        String text = provider == Provider.OPENROUTER ? parts.get(0) : String.join("", parts);
        return temizle(text);
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
