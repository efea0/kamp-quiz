package quiz.web;

import com.sun.net.httpserver.HttpExchange;
import quiz.core.Quiz;
import quiz.core.QuizSet;
import quiz.model.Question;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Sunum modu" ekranlari — hocanin bir oda acip sinifi yonettigi akis:
 *
 *   /kur    Hocanin oda actigi sayfa: hazir testlerden birini secer
 *   /oda    Hoca paneli: oda kodu, katilimcilar, senkron akis dugmeleri
 *   /ekran  Buyuk ekrana yansitilan canli siralama
 *   /rapor  Hangi soru en cok yanlis yapildi
 */
public final class RoomPages {

    private final ServerContext ctx;

    public RoomPages(ServerContext ctx) {
        this.ctx = ctx;
    }

    /** Hocanin oda actigi sayfa: hazir testlerden birini secer. */
    public void handleHostSetup(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            Map<String, String> form = ctx.readForm(exchange);
            QuizSet set = ctx.findSet(form.get("set"));
            if (set == null) {
                ctx.redirect(exchange, "/kur");
                return;
            }
            Room.Mode mode = "senkron".equals(form.get("mod"))
                    ? Room.Mode.SENKRON : Room.Mode.SERBEST;
            boolean sharedOrder = !"kisiye-ozel".equals(form.get("sira"));
            Room room = new Room(ctx.newRoomCode(), set, mode, sharedOrder);
            ctx.getRooms().put(room.getCode(), room);
            ctx.redirect(exchange, "/oda?kod=" + room.getCode());
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

        String body = """
                <div class="screen">
                  <p class="eyebrow">Sunum modu</p>
                  <h1>Oda kur</h1>
                  <p class="muted small">Bir test seç. Katılımcılara 4 haneli bir kod vereceğiz.</p>

                  <form method="POST" action="/kur" style="margin-top:20px">
                    <div class="card" style="margin-bottom:16px">
                      <label class="field" for="mod">Akış</label>
                      <select id="mod" name="mod">
                        <option value="serbest" selected>Serbest — herkes kendi hızında ilerler</option>
                        <option value="senkron">Senkron — herkes aynı soruda, sen ilerletirsin</option>
                      </select>

                      <label class="field" for="sira">Soru sırası</label>
                      <select id="sira" name="sira" style="margin-bottom:0">
                        <option value="paylasik" selected>Herkese aynı — perdedeki yarış anlamlı olur</option>
                        <option value="kisiye-ozel">Kişiye özel — yandakinden kopyalanamaz</option>
                      </select>
                    </div>
                    <div class="setlist">
                %s        </div>
                  </form>

                  <div class="actions">
                    <a class="plain center" href="/">Ana sayfaya dön</a>
                  </div>
                </div>
                """.formatted(cards);

        ctx.sendHtml(exchange, 200, Html.page("Oda kur", body));
    }

    /** Hocanin paneli: kod, katilimcilar ve projeksiyon baglantisi. */
    public void handleHostPanel(HttpExchange exchange) throws IOException {
        Room room = ctx.getRooms().get(ServerContext.query(exchange, "kod"));
        if (room == null) {
            ctx.redirect(exchange, "/kur");
            return;
        }

        // Senkron odada hoca akisi buradan yonetir.
        if ("POST".equals(exchange.getRequestMethod())) {
            switch (ctx.readForm(exchange).getOrDefault("islem", "")) {
                case "basla"   -> room.start(ctx.getAllQuestions());
                case "goster"  -> room.reveal();
                case "sonraki" -> room.next(ctx.getAllQuestions());
                default -> { }
            }
            ctx.redirect(exchange, "/oda?kod=" + room.getCode());
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
                    .append(ServerContext.progressLabel(player))
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
                  <p class="muted small center" style="margin-top:-4px">Soru sırası: <b>%s</b></p>

                  <div class="rank">
                %s      </div>

                %s
                  <div class="actions">
                    <a class="btn blue" href="/ekran?kod=%s">Projeksiyon ekranı</a>
                    <a class="btn ghost" href="/rapor?kod=%s">Yanlış raporu</a>
                    <a class="btn ghost" href="/disaktar/oda?kod=%s">Sonuçları indir (CSV)</a>
                    <a class="btn ghost" href="/disaktar/sorular?kod=%s">Soru analizini indir (CSV)</a>
                    <a class="plain center" href="/">Ana sayfa</a>
                  </div>
                </div>
                """.formatted(
                Html.escape(room.getSet().getName()),
                room.getCode(),
                ctx.joinQr(exchange, 190),
                Html.escape(ctx.joinUrl(exchange)),
                room.playerCount(),
                room.getSet().totalQuestions(),
                room.isSharedOrder() ? "herkese aynı" : "kişiye özel",
                list,
                hostControls(room),
                room.getCode(), room.getCode(), room.getCode(), room.getCode());

        ctx.sendHtml(exchange, 200, Html.page("Oda " + room.getCode(), body,
                "  <meta http-equiv=\"refresh\" content=\"4\">\n"));
    }

    /** Buyuk ekranda gosterilen canli siralama. Kendi kendine yenilenir. */
    public void handleScreen(HttpExchange exchange) throws IOException {
        Room room = ctx.getRooms().get(ServerContext.query(exchange, "kod"));
        if (room == null) {
            ctx.redirect(exchange, "/kur");
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
                    .append("</span><span class=\"sub\">").append(ServerContext.progressLabel(player))
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
                ctx.joinQr(exchange, 150),
                Html.escape(ctx.joinUrl(exchange)),
                list,
                room.playerCount(),
                room.everyoneFinished() ? "test bitti" : "devam ediyor");

        ctx.sendHtml(exchange, 200, Html.page("Ekran " + room.getCode(), body,
                "  <meta http-equiv=\"refresh\" content=\"3\">\n"));
    }

    /** Hoca icin yanlis raporu: hangi soru en cok yanlis yapildi. */
    public void handleReport(HttpExchange exchange) throws IOException {
        Room room = ctx.getRooms().get(ServerContext.query(exchange, "kod"));
        if (room == null) {
            ctx.redirect(exchange, "/kur");
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
                    <a class="btn ghost" href="/disaktar/oda?kod=%s">Sonuçları indir (CSV)</a>
                    <a class="btn ghost" href="/disaktar/sorular?kod=%s">Soru analizini indir (CSV)</a>
                  </div>
                </div>
                """.formatted(
                Html.escape(room.getSet().getName()), room.getCode(), list,
                room.getCode(), room.getCode(), room.getCode(), room.getCode());

        ctx.sendHtml(exchange, 200, Html.page("Yanlış raporu", body));
    }

    /** Senkron odada hocanin akis dugmeleri. Serbest odada bos doner. */
    private String hostControls(Room room) {
        if (!room.isSynchronous()) {
            return "";
        }

        int total = room.questionCount(ctx.getAllQuestions());
        String durum;
        String buton;

        switch (room.getPhase()) {
            case LOBI -> {
                durum = "Katılımcılar bekleniyor";
                buton = "<button class=\"btn\" type=\"submit\" name=\"islem\" value=\"basla\">Başlat</button>";
            }
            case SORU -> {
                durum = "Soru " + (room.getIndex() + 1) + " / " + total
                        + " · " + room.remainingSeconds() + " sn kaldı";
                buton = "<button class=\"btn blue\" type=\"submit\" name=\"islem\" value=\"goster\">Cevabı göster</button>";
            }
            case CEVAP -> {
                durum = "Soru " + (room.getIndex() + 1) + " / " + total + " · cevap açıkta";
                boolean son = room.getIndex() + 1 >= total;
                buton = "<button class=\"btn\" type=\"submit\" name=\"islem\" value=\"sonraki\">"
                        + (son ? "Testi bitir" : "Sonraki soru") + "</button>";
            }
            default -> {
                durum = "Test bitti";
                buton = "";
            }
        }

        return """
                  <div class="card" style="margin-top:4px">
                    <p class="eyebrow" style="margin-bottom:10px">Akış kontrolü</p>
                    <p class="muted small" style="margin-bottom:14px">%s</p>
                    <form method="POST" action="/oda?kod=%s">%s</form>
                  </div>
                """.formatted(Html.escape(durum), room.getCode(), buton);
    }
}
