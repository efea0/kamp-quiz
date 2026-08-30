package quiz.web;

import com.sun.net.httpserver.HttpExchange;
import quiz.core.Scoreboard;

import java.io.IOException;
import java.util.List;

public final class BoardPage {

    private final ServerContext ctx;

    public BoardPage(ServerContext ctx) {
        this.ctx = ctx;
    }


    public void handleBoard(HttpExchange exchange) throws IOException {
        List<Scoreboard.Entry> entries;
        try {
            entries = ctx.getScoreboard().topScores(20);
        } catch (IOException e) {
            entries = List.of();
        }

        GameSession session = ctx.currentSession(exchange);
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

        ctx.sendHtml(exchange, 200, Html.page("Lider Tablosu", body));
    }
}
