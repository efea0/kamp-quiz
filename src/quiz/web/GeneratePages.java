package quiz.web;

import com.sun.net.httpserver.HttpExchange;
import quiz.core.QuestionBank;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public final class GeneratePages {

    private final ServerContext ctx;

    public GeneratePages(ServerContext ctx) {
        this.ctx = ctx;
    }


    public void handleGenerate(HttpExchange exchange) throws IOException {
        if (!ctx.getGenerator().isEnabled()) {
            ctx.sendHtml(exchange, 200, Html.page("AI kapalı", """
                    <div class="screen">
                      <p class="eyebrow">Soru üretici</p>
                      <h1>AI özelliği kapalı</h1>
                      <div class="card">
                        <p class="muted small">Bu özellik bir Google Gemini API anahtarı gerektirir.
                        Anahtar <b>koda yazılmaz</b>, ortam değişkeninden okunur:</p>
                        <p class="muted small">En güvenli yol, anahtarı bir dosyaya koymak
                        ve dosyayı sadece kendine okunur yapmak:</p>
                        <pre class="code-block">mkdir -p ~/.config/kamp-quiz
printf '%s' "ANAHTAR" > ~/.config/kamp-quiz/gemini.key
chmod 600 ~/.config/kamp-quiz/gemini.key
./run.sh web</pre>
                        <p class="muted small">Program bu dosyayı kendiliğinden bulur.
                        OpenRouter için dosya adı <code>openrouter.key</code>. İkisi de varsa
                        önce Gemini denenir, hata verirse OpenRouter'a geçilir.</p>
                        <p class="muted small">Ortam değişkeni de çalışır
                        (<code>GEMINI_API_KEY</code>) ama kabuk geçmişine düşebilir.
                        Anahtarı asla depoya ekleme.</p>
                      </div>
                      <div class="actions"><a class="btn" href="/">Ana sayfa</a></div>
                    </div>
                    """));
            return;
        }


        if (!ctx.getAdminKey().isEmpty() && !ctx.isAdmin(exchange)) {
            if ("POST".equals(exchange.getRequestMethod())) {
                String given = ctx.readForm(exchange).getOrDefault("parola", "");
                if (java.security.MessageDigest.isEqual(
                        given.getBytes(StandardCharsets.UTF_8),
                        ctx.getAdminKey().getBytes(StandardCharsets.UTF_8))) {
                    String token = UUID.randomUUID().toString();
                    ctx.getAdminTokens().add(token);
                    exchange.getResponseHeaders().add("Set-Cookie",
                            "qadmin=" + token + "; Path=/; Max-Age=28800; SameSite=Lax");
                    ctx.redirect(exchange, "/uret");
                    return;
                }
                ctx.sendHtml(exchange, 200, Html.page("Giriş", adminForm("Parola yanlış.")));
                return;
            }
            ctx.sendHtml(exchange, 200, Html.page("Giriş", adminForm(null)));
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod())) {
            ctx.sendHtml(exchange, 200, Html.page("Soru üret", generateForm(null)));
            return;
        }

        Map<String, String> form = ctx.readForm(exchange);
        String action = form.getOrDefault("islem", "uret");
        String title = form.getOrDefault("baslik", "").trim();

        try {
            switch (action) {
                case "duzenle" -> {
                    String draft = form.getOrDefault("taslak", "");
                    String instruction = form.getOrDefault("talimat", "").trim();
                    if (instruction.isEmpty()) {
                        ctx.sendHtml(exchange, 200, Html.page("Taslak",
                                draftEditor(title, draft, "Ne değiştirmemi istediğini yaz.")));
                        return;
                    }
                    String revised = ctx.getGenerator().revise(draft, instruction);
                    ctx.sendHtml(exchange, 200, Html.page("Taslak", draftEditor(title, revised, null)));
                }
                case "kaydet" -> {
                    String draft = form.getOrDefault("taslak", "");
                    Path saved = saveDraft(title, draft);
                    ctx.reloadContent();
                    ctx.sendHtml(exchange, 200, Html.page("Kaydedildi", savedScreen(title, saved)));
                }
                default -> {
                    String topic = form.getOrDefault("konu", "").trim();
                    if (topic.isEmpty()) {
                        ctx.sendHtml(exchange, 200, Html.page("Soru üret", generateForm("Bir konu yaz.")));
                        return;
                    }
                    int count = Math.max(3, Math.min(25, ServerContext.parseIntOr(form.get("adet"), 10)));
                    String level = form.getOrDefault("seviye", "giriş");
                    String draft = ctx.getGenerator().generate(topic, count, level);
                    ctx.sendHtml(exchange, 200, Html.page("Taslak",
                            draftEditor(title.isEmpty() ? topic : title, draft, null)));
                }
            }
        } catch (IOException e) {
            ctx.sendHtml(exchange, 200, Html.page("Hata", generateForm(e.getMessage())));
        } catch (IllegalArgumentException e) {
            ctx.sendHtml(exchange, 200, Html.page("Hata",
                    draftEditor(title, form.getOrDefault("taslak", ""), e.getMessage())));
        }
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

        if (ctx.getAdminKey().isEmpty()) {
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
                """.formatted(Html.escape(ctx.getGenerator().describe()), warning);
    }


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


    private Path uniquePath(String slug) {
        Path candidate = ctx.getQuestionsDir().resolve(slug + ".txt");
        int n = 2;
        while (Files.exists(candidate)) {
            candidate = ctx.getQuestionsDir().resolve(slug + "-" + n++ + ".txt");
        }
        return candidate;
    }
}
