package quiz.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    /** Anahtar dosyalarinin varsayilan yeri. */
    private static final Path KEY_DIR =
            Path.of(System.getProperty("user.home"), ".config", "kamp-quiz");

    /**
     * Yonerge (prompt) dosyalarinin okundugu klasor. Calisma dizinine gore
     * gorelidir (questions/ ve sets/ klasorleriyle ayni mantik).
     */
    private static final Path PROMPTS_DIR = Path.of("prompts");

    /** Hangi servise konusuyoruz. */
    public enum Provider { GEMINI, OPENROUTER }

    /** Tek bir servis tanimi. */
    private record Endpoint(Provider provider, String apiKey, String baseUrl, String model) {
        String label() {
            return (provider == Provider.OPENROUTER ? "OpenRouter" : "Gemini") + " · " + model;
        }
    }

    /**
     * Denenecek servisler, SIRAYLA. Ilki basarisiz olursa ikincisine gecilir.
     * Varsayilan sira: once Gemini (ucretsiz kota), sonra OpenRouter.
     */
    private final List<Endpoint> endpoints;

    /** Baslangicta kullaniciya gosterilecek uyarilar (or. dosya izinleri). */
    private final List<String> warnings = new ArrayList<>();

    private final HttpClient http;

    public QuestionGenerator() {
        List<Endpoint> found = new ArrayList<>();

        String geminiKey = resolveKey("GEMINI_API_KEY", "gemini.key", warnings);
        if (!geminiKey.isEmpty()) {
            found.add(new Endpoint(Provider.GEMINI, geminiKey,
                    stripSlash(envOr("GEMINI_BASE_URL", GEMINI_BASE)),
                    envOr("GEMINI_MODEL", GEMINI_MODEL)));
        }

        String routerKey = resolveKey("OPENROUTER_API_KEY", "openrouter.key", warnings);
        if (!routerKey.isEmpty()) {
            found.add(new Endpoint(Provider.OPENROUTER, routerKey,
                    stripSlash(envOr("OPENROUTER_BASE_URL", OPENROUTER_BASE)),
                    envOr("OPENROUTER_MODEL", OPENROUTER_MODEL)));
        }

        // AI_PROVIDER yazilmissa o servis basa alinir.
        String preferred = trim(System.getenv("AI_PROVIDER")).toLowerCase();
        if (!preferred.isEmpty()) {
            Provider first = preferred.startsWith("openrouter") || preferred.startsWith("openai")
                    ? Provider.OPENROUTER : Provider.GEMINI;
            found.sort((a, b) -> Boolean.compare(b.provider() == first, a.provider() == first));
        }

        this.endpoints = List.copyOf(found);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    /** Test icin: tek bir servisi dogrudan verir. */
    QuestionGenerator(Provider provider, String apiKey, String baseUrl, String model) {
        this.endpoints = List.of(new Endpoint(provider, trim(apiKey), stripSlash(baseUrl), model));
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    /**
     * Anahtari sirayla arar:
     *   1) XXX_API_KEY_FILE  -> gosterilen dosyanin icerigi
     *   2) XXX_API_KEY       -> ortam degiskeninin kendisi
     *   3) ~/.config/kamp-quiz/<varsayilan dosya>
     *
     * Dosyadan okumak ortam degiskeninden daha guvenlidir: ortam degiskeni
     * kabuk gecmisine dusebilir ve `env` ciktisinda gorunur; dosya ise 600
     * izniyle korunabilir.
     */
    private static String resolveKey(String envName, String defaultFile, List<String> warnings) {
        String fromFileEnv = trim(System.getenv(envName + "_FILE"));
        if (!fromFileEnv.isEmpty()) {
            return readKeyFile(Path.of(fromFileEnv), warnings, true);
        }

        String direct = trim(System.getenv(envName));
        if (!direct.isEmpty()) {
            return direct;
        }

        Path defaultPath = KEY_DIR.resolve(defaultFile);
        if (Files.isReadable(defaultPath)) {
            return readKeyFile(defaultPath, warnings, false);
        }
        return "";
    }

    /** Anahtar dosyasini okur ve izinlerini denetler. */
    private static String readKeyFile(Path path, List<String> warnings, boolean required) {
        try {
            String key = Files.readString(path, StandardCharsets.UTF_8).strip();
            if (key.isEmpty()) {
                warnings.add("Anahtar dosyası boş: " + path);
                return "";
            }
            checkPermissions(path, warnings);
            return key;
        } catch (IOException e) {
            if (required) {
                warnings.add("Anahtar dosyası okunamadı: " + path + " (" + e.getMessage() + ")");
            }
            return "";
        }
    }

    /** Dosyayi baskalari da okuyabiliyorsa uyarir. */
    private static void checkPermissions(Path path, List<String> warnings) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
            boolean open = perms.contains(PosixFilePermission.GROUP_READ)
                    || perms.contains(PosixFilePermission.OTHERS_READ);
            if (open) {
                warnings.add("Anahtar dosyası başkaları tarafından okunabilir: " + path
                        + "  ->  düzeltmek için:  chmod 600 " + path);
            }
        } catch (UnsupportedOperationException | IOException e) {
            // Windows'ta POSIX izinleri yok; sessizce gec.
        }
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

    /** En az bir servis tanimli mi? Degilse uretim ozelligi kapali gosterilir. */
    public boolean isEnabled() {
        return !endpoints.isEmpty();
    }

    /** Ekranda gosterilecek etiket: "Gemini · model  →  OpenRouter · model" */
    public String describe() {
        if (endpoints.isEmpty()) {
            return "tanımsız";
        }
        StringBuilder out = new StringBuilder();
        for (Endpoint endpoint : endpoints) {
            if (!out.isEmpty()) {
                out.append("  →  ");
            }
            out.append(endpoint.label());
        }
        return out.toString();
    }

    /** Baslangic uyarilari (anahtar dosyasi izinleri gibi). */
    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }

    /**
     * Gomulu (varsayilan) uretim yonergesi. Yer tutucular:
     *   {seviye}  -> zorluk seviyesi (kolay/orta/zor)
     *   {konu}    -> konu basligi
     *   {adet}    -> istenen soru sayisi
     *
     * prompts/soru-uret.txt dosyasi varsa BUNUN yerine o kullanilir.
     * Ornek dosya: prompts/soru-uret.txt.ornek (uzantisini silip etkinlestirin).
     */
    private static final String DEFAULT_GENERATE_PROMPT = """
            Sen bir bilgi yarismasi soru yazarisin. Turkce, {seviye} seviyesinde,
            "{konu}" konusunda TAM {adet} adet coktan secmeli soru yaz.

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
            """;

    /**
     * Gomulu (varsayilan) duzenleme yonergesi. Yer tutucular:
     *   {talimat} -> kullanicinin duzenleme istegi
     *   {paket}   -> duzenlenecek mevcut soru paketi metni
     *
     * prompts/soru-duzenle.txt dosyasi varsa BUNUN yerine o kullanilir.
     * Ornek dosya: prompts/soru-duzenle.txt.ornek (uzantisini silip etkinlestirin).
     */
    private static final String DEFAULT_REVISE_PROMPT = """
            Asagida bir bilgi yarismasi soru paketi var. Kullanicinin istegine gore
            bu paketi duzenle ve TAM AYNI BICIMDE geri ver.

            KULLANICININ ISTEGI:
            {talimat}

            BICIM KURALLARI (degistirme):
            Soru metni | sik1 | sik2 | sik3 | sik4 | dogruNo
            > Dogru cevabin kisa nedeni.

            - Sadece duzenlenmis paketi yaz; aciklama, giris cumlesi, markdown, kod blogu YOK.
            - Dikey cizgi sadece ayirici olarak kullanilir.
            - dogruNo 1 ile 4 arasindadir.

            MEVCUT PAKET:
            {paket}
            """;

    /** Verilen konuda yeni bir soru paketi metni uretir. */
    public String generate(String topic, int count, String level) throws IOException {
        String prompt = promptText("soru-uret.txt", DEFAULT_GENERATE_PROMPT)
                .replace("{seviye}", level)
                .replace("{konu}", topic)
                .replace("{adet}", String.valueOf(count));

        return callModel(prompt);
    }

    /** Var olan taslagi verilen talimata gore yeniden yazar. */
    public String revise(String draft, String instruction) throws IOException {
        String prompt = promptText("soru-duzenle.txt", DEFAULT_REVISE_PROMPT)
                .replace("{talimat}", instruction)
                .replace("{paket}", draft);

        return callModel(prompt);
    }

    /**
     * prompts/&lt;dosyaAdi&gt; dosyasini okur; varsa ve okunabiliyorsa onu
     * doner (yorum satirlari ayiklanmis halde), yoksa ya da herhangi bir
     * sebeple okunamazsa SESSIZCE gomulu (builtin) metne duser. Bu yuzden
     * dosya sistemiyle ilgili hicbir sorun uygulamanin calismasini durdurmaz.
     */
    private static String promptText(String fileName, String builtin) {
        Path path = PROMPTS_DIR.resolve(fileName);
        try {
            if (Files.isRegularFile(path) && Files.isReadable(path)) {
                String withoutComments = stripCommentLines(Files.readString(path, StandardCharsets.UTF_8));
                if (!withoutComments.isBlank()) {
                    return withoutComments;
                }
            }
        } catch (IOException | RuntimeException e) {
            // Dosya okunamadi (izin, bozuk kodlama, vb.) - gomulu metne dusuluyor.
        }
        return builtin;
    }

    /**
     * '#' ile baslayan satirlari atlar. Boylece kullanicilar yonerge
     * dosyalarinin basina yer tutuculari aciklayan yorum satirlari
     * ekleyebilir; bu satirlar modele gonderilen metne dahil edilmez.
     */
    private static String stripCommentLines(String text) {
        StringBuilder result = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (line.strip().startsWith("#")) {
                continue;
            }
            result.append(line).append('\n');
        }
        return result.toString().strip();
    }

    /**
     * Tanimli servisleri SIRAYLA dener. Ilki hata verirse (kota dolmus,
     * servis kapali, model bulunamadi) sessizce ikincisine gecer.
     * Hepsi basarisiz olursa toplu hata mesaji doner.
     */
    private String callModel(String prompt) throws IOException {
        if (!isEnabled()) {
            throw new IOException("API anahtarı tanımlı değil.");
        }

        StringBuilder problems = new StringBuilder();
        for (Endpoint endpoint : endpoints) {
            try {
                return callEndpoint(endpoint, prompt);
            } catch (IOException e) {
                if (!problems.isEmpty()) {
                    problems.append(" | ");
                }
                problems.append(endpoint.label()).append(": ").append(e.getMessage());
            }
        }
        throw new IOException(problems.toString());
    }

    /** Tek bir servise istek gonderir. */
    private String callEndpoint(Endpoint endpoint, String prompt) throws IOException {
        String escaped = Json.escape(prompt);
        String body;
        HttpRequest.Builder builder;

        if (endpoint.provider() == Provider.OPENROUTER) {
            // OpenAI uyumlu bicim; OpenRouter disindaki cogu servis de bunu kullanir.
            body = """
                    {"model":"%s","messages":[{"role":"user","content":"%s"}],"temperature":0.7}
                    """.formatted(Json.escape(endpoint.model()), escaped);
            builder = HttpRequest.newBuilder(URI.create(endpoint.baseUrl() + "/chat/completions"))
                    .header("Authorization", "Bearer " + endpoint.apiKey());
        } else {
            body = """
                    {"contents":[{"parts":[{"text":"%s"}]}],
                     "generationConfig":{"temperature":0.7,"maxOutputTokens":4096}}
                    """.formatted(escaped);
            builder = HttpRequest.newBuilder(URI.create(endpoint.baseUrl()
                            + "/v1beta/models/" + endpoint.model() + ":generateContent"))
                    .header("x-goog-api-key", endpoint.apiKey());
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
            throw new IOException("İstek yarıda kesildi.");
        } catch (IOException e) {
            throw new IOException("ulaşılamadı (" + maskele(e.getMessage(), endpoint.apiKey()) + ")");
        }

        if (response.statusCode() != 200) {
            List<String> messages = Json.valuesOf(response.body(), "message");
            String detail = messages.isEmpty() ? kisalt(response.body()) : messages.get(0);
            throw new IOException("HTTP " + response.statusCode() + " "
                    + maskele(detail, endpoint.apiKey()));
        }

        // Gemini metni "text", OpenAI uyumlu servisler "content" altinda dondurur.
        String key = endpoint.provider() == Provider.OPENROUTER ? "content" : "text";
        List<String> parts = Json.valuesOf(response.body(), key);
        parts.removeIf(String::isBlank);
        if (parts.isEmpty()) {
            throw new IOException("boş cevap döndü");
        }

        String text = endpoint.provider() == Provider.OPENROUTER
                ? parts.get(0) : String.join("", parts);
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
    private static String maskele(String text, String apiKey) {
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
