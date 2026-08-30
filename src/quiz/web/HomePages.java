package quiz.web;

import com.sun.net.httpserver.HttpExchange;
import quiz.core.Quiz;
import quiz.core.QuestionBank;
import quiz.core.QuizSet;
import quiz.model.Question;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HomePages {

    private final ServerContext ctx;

    public HomePages(ServerContext ctx) {
        this.ctx = ctx;
    }


    public void handleHome(HttpExchange exchange) throws IOException {
        if (!"/".equals(exchange.getRequestURI().getPath())) {
            ctx.sendHtml(exchange, 404, Html.page("Bulunamadı", """
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
        for (QuizSet set : ctx.getSets()) {
            cards.append("      <button class=\"setcard\" type=\"submit\" name=\"set\" value=\"")
                 .append(Html.escape(set.getName())).append("\">")
                 .append("<b>").append(Html.escape(set.getName())).append("</b>")
                 .append("<small>").append(Html.escape(set.getDescription())).append("</small>")
                 .append("<em>").append(set.totalQuestions()).append(" soru · ")
                 .append(set.getTimeLimitSeconds()).append(" sn</em>")
                 .append("</button>\n");
        }
        if (ctx.getSets().isEmpty()) {
            cards.append("      <p class=\"muted small\">Hazır test bulunamadı. sets/ klasörüne bir .txt ekleyebilirsin.</p>\n");
        }

        String invitedCode = ServerContext.query(exchange, "kod");
        String body = """
                <div class="screen">
                  <p class="eyebrow">Kamp Quiz</p>
                  <h1>Hangi testi çözelim?</h1>
                  <p class="muted small">%d soruluk havuz · hızlı cevap daha çok puan getirir</p>

                  <form method="POST" action="/katil" class="joinbox">
                    <label class="field" for="kod">Oda kodun varsa</label>
                    <div class="joinrow">
                      <input type="text" id="kod" name="kod" inputmode="numeric" maxlength="4"
                             pattern="[0-9]{4}" placeholder="0000" class="codeinput" value="%s" required>
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
                """.formatted(ctx.getAllQuestions().size(), Html.escape(invitedCode), cards);

        ctx.sendHtml(exchange, 200, Html.page("Kamp Quiz", body));
    }


    public void handleCustom(HttpExchange exchange) throws IOException {
        List<String> categories = QuestionBank.categoriesOf(ctx.getAllQuestions());

        StringBuilder options = new StringBuilder();
        options.append("            <option value=\"\">Hepsi karışık — ")
               .append(ctx.getAllQuestions().size()).append(" soru</option>\n");
        for (String category : categories) {
            int count = QuestionBank.byCategory(ctx.getAllQuestions(), category).size();
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

        ctx.sendHtml(exchange, 200, Html.page("Kendin ayarla", body));
    }


    public void handleStart(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            ctx.redirect(exchange, "/");
            return;
        }

        Map<String, String> form = ctx.readForm(exchange);
        String name = ServerContext.cleanName(form.get("isim"));

        List<Question> pool;
        int count;
        int seconds;

        QuizSet set = ctx.findSet(form.get("set"));
        if (set != null) {

            pool = set.build(ctx.getAllQuestions());
            count = pool.size();
            seconds = set.getTimeLimitSeconds();
        } else {
            String category = form.getOrDefault("kategori", "");
            pool = category.isEmpty()
                    ? ctx.getAllQuestions()
                    : QuestionBank.byCategory(ctx.getAllQuestions(), category);
            if (pool.isEmpty()) {
                pool = ctx.getAllQuestions();
            }
            count = ServerContext.parseIntOr(form.get("adet"), 10);
            seconds = ServerContext.parseIntOr(form.get("sure"), 20);
        }

        Quiz quiz = new Quiz(pool);
        quiz.shuffle();
        quiz.limitTo(count);
        quiz.setTimeLimitSeconds(seconds);

        String sessionId = UUID.randomUUID().toString();
        ctx.getSessions().put(sessionId, new GameSession(name, quiz, null));
        ctx.setSessionCookie(exchange, sessionId);
        ctx.redirect(exchange, "/quiz");
    }


    public void handleJoin(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            ctx.redirect(exchange, "/");
            return;
        }

        Map<String, String> form = ctx.readForm(exchange);
        Room room = ctx.getRooms().get(form.getOrDefault("kod", "").trim());
        if (room == null) {
            ctx.sendHtml(exchange, 404, Html.page("Oda bulunamadı", """
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


        GameSession existing = ctx.currentSession(exchange);
        if (existing != null && room.getCode().equals(existing.getRoomCode())) {
            ctx.redirect(exchange, "/quiz");
            return;
        }

        String name = ServerContext.cleanName(form.get("isim"));
        if (!room.reserveName(name)) {
            ctx.sendHtml(exchange, 409, Html.page("İsim alınmış", """
                    <div class="screen">
                      <div class="card center">
                        <h1>Bu isim alınmış</h1>
                        <p class="muted">Odada zaten <b>%s</b> adında biri var.
                        Başka bir isim dene.</p>
                      </div>
                      <div class="actions"><a class="btn" href="/">Geri dön</a></div>
                    </div>
                    """.formatted(Html.escape(name))));
            return;
        }

        GameSession session = new GameSession(name, room.newQuiz(ctx.getAllQuestions()), room.getCode());
        room.addPlayer(session);

        String sessionId = UUID.randomUUID().toString();
        ctx.getSessions().put(sessionId, session);
        ctx.setSessionCookie(exchange, sessionId);
        ctx.redirect(exchange, "/quiz");
    }
}
