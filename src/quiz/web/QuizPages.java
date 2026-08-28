package quiz.web;

import com.sun.net.httpserver.HttpExchange;
import quiz.core.Quiz;
import quiz.model.Question;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bir oyuncunun quiz sirasinda gordugu ekranlar:
 *
 *   /quiz    Siradaki soru (ya da az once verilen cevabin sonucu)
 *   /cevap   Cevabi degerlendirir, sonraki soruya yonlendirir
 *   /devam   "Devam" tusu: sonuc ekranindan sonraki soruya gecer
 *   /sonuc   Test bitince skor ekrani
 *   /tekrar  Yanlis yapilan sorulari yeni bir tur olarak baslatir
 *
 * Bir oda SENKRON modda ise (hoca herkesi ayni soruda tutuyorsa) /quiz
 * isteği burada Room'un durumuna gore ayri bir ekrana (sendSyncScreen)
 * yonlendirilir; serbest oyunda normal akis isler.
 */
public final class QuizPages {

    private final ServerContext ctx;

    /**
     * Bu turda hangi sik hangi soruda isaretlendi; quiz.getHistory() ile ayni
     * sirada tutulur. Quiz.AnswerResult isaretlenen sikki tasimiyor (yalnizca
     * dogru/yanlis, sure ve soruyu), ama sonuc ekraninda "senin cevabin"i
     * gostermek icin buna ihtiyacimiz var. Anahtar Quiz NESNESININ KENDISI
     * (Quiz equals/hashCode override etmiyor, yani kimlik bazli); her yeni
     * Quiz (orn. /tekrar ile baslayan tur) kendi bos listesiyle baslar.
     */
    private final Map<Quiz, List<Integer>> chosenAnswers = new ConcurrentHashMap<>();

    public QuizPages(ServerContext ctx) {
        this.ctx = ctx;
    }

    /** Sirdaki soruyu ya da az once verilen cevabin sonucunu gosterir. */
    public void handleQuiz(HttpExchange exchange) throws IOException {
        GameSession session = ctx.currentSession(exchange);
        if (session == null) {
            ctx.redirect(exchange, "/");
            return;
        }

        // Senkron odada akisi oda yonetir; serbest akis kurallari isletilmez.
        Room room = session.getRoomCode() == null ? null : ctx.getRooms().get(session.getRoomCode());
        if (room != null && room.isSynchronous()) {
            sendSyncScreen(exchange, session, room);
            return;
        }

        // Cevap verildiyse once sonuc ekrani gosterilir ("Devam" ile gecilir).
        if (session.getFeedback() != null) {
            ctx.sendHtml(exchange, 200, Html.page("Cevap", reviewScreen(session)));
            return;
        }

        Quiz quiz = session.getQuiz();
        if (!quiz.hasNext()) {
            ctx.redirect(exchange, "/sonuc");
            return;
        }

        // Sayac soru ekrana gelince baslar. Ayni soru icin ikinci cagri sifirlamaz.
        quiz.startQuestionTimer();

        String body = questionScreen(quiz, quiz.remainingSeconds(), quiz.getTimeLimitSeconds(),
                quiz.getQuestionNumber(), quiz.getTotal());

        ctx.sendHtml(exchange, 200, Html.page("Soru " + quiz.getQuestionNumber(), body));
    }

    /** Cevabi degerlendirir ve bir sonraki soruya yonlendirir. */
    public void handleAnswer(HttpExchange exchange) throws IOException {
        GameSession session = ctx.currentSession(exchange);
        if (session == null) {
            ctx.redirect(exchange, "/");
            return;
        }

        Quiz quiz = session.getQuiz();
        if (!"POST".equals(exchange.getRequestMethod()) || !quiz.hasNext()) {
            ctx.redirect(exchange, "/quiz");
            return;
        }

        Question question = quiz.currentQuestion();
        int answer = ServerContext.parseIntOr(ctx.readForm(exchange).get("cevap"), -1);
        Quiz.AnswerResult result = quiz.submitAnswer(answer);
        chosenAnswers.computeIfAbsent(quiz, k -> new CopyOnWriteArrayList<>()).add(answer);

        session.setFeedback(new GameSession.Feedback(
                result.correct(), result.timedOut(), result.earnedPoints(), question, answer));

        // Cevaptan sonra yonlendiriyoruz ki kullanici sayfayi yenileyince
        // ayni cevap tekrar gonderilmesin (POST-Redirect-GET deseni).
        ctx.redirect(exchange, "/quiz");
    }

    /** "Devam" tusu: sonucu temizler, sonraki soruya gecer. */
    public void handleContinue(HttpExchange exchange) throws IOException {
        GameSession session = ctx.currentSession(exchange);
        if (session == null) {
            ctx.redirect(exchange, "/");
            return;
        }
        session.clearFeedback();
        ctx.redirect(exchange, session.getQuiz().hasNext() ? "/quiz" : "/sonuc");
    }

    /** Sonuc sayfasi; skoru bir kez kaydeder. */
    public void handleResult(HttpExchange exchange) throws IOException {
        GameSession session = ctx.currentSession(exchange);
        if (session == null) {
            ctx.redirect(exchange, "/");
            return;
        }

        Quiz quiz = session.getQuiz();
        if (!session.isScoreSaved()) {
            try {
                ctx.getScoreboard().save(session.getPlayerName(), quiz.getScore(),
                        quiz.getTotal(), quiz.getPoints());
                session.markScoreSaved();
            } catch (IOException e) {
                System.out.println("Skor kaydedilemedi: " + e.getMessage());
            }
        }

        String body = """
                <div class="screen">
                  <p class="eyebrow">%s</p>
                  <h1>%s</h1>
                  <div class="bigscore">%d</div>
                  <p class="muted small">toplam puan</p>

                  <div class="stats" style="margin-top:22px">
                    <div class="stat"><b>%d/%d</b><span>Doğru</span></div>
                    <div class="stat"><b>%%%d</b><span>Başarı</span></div>
                  </div>
                %s%s%s
                  <div class="actions">
                %s      <a class="btn blue" href="/tablo">Lider tablosu</a>
                    <a class="btn ghost" href="/">Yeniden oyna</a>
                  </div>
                </div>
                """.formatted(
                Html.escape(session.getPlayerName()),
                verdictTitle(quiz.getPercentage()),
                quiz.getPoints(),
                quiz.getScore(), quiz.getTotal(), quiz.getPercentage(),
                categoryBreakdown(quiz), speedSummary(quiz), wrongReview(quiz),
                retryButton(quiz));

        ctx.sendHtml(exchange, 200, Html.page("Sonuç", body));
    }

    /** Yanlis yapilan sorulari yeni bir tur olarak sunar. */
    public void handleRetry(HttpExchange exchange) throws IOException {
        String sessionId = ctx.currentSessionId(exchange);
        GameSession previous = sessionId == null ? null : ctx.getSessions().get(sessionId);
        if (previous == null) {
            ctx.redirect(exchange, "/");
            return;
        }

        List<Question> wrong = previous.getQuiz().getWrongQuestions();
        // Yanlis yoksa "ayni testi tekrar coz": tum sorular, cevaplanmis
        // sirayla (previous.getQuiz() /sonuc'a ulastigina gore tamamen
        // oynanmis olmali, yani gecmis tum sorulari icerir).
        List<Question> pool = wrong.isEmpty() ? allAnsweredQuestions(previous.getQuiz()) : wrong;
        if (pool.isEmpty()) {
            ctx.redirect(exchange, "/sonuc");
            return;
        }

        Quiz retry = new Quiz(pool);
        retry.shuffle();
        retry.setTimeLimitSeconds(previous.getQuiz().getTimeLimitSeconds());

        // Tekrar turu odanin siralamasina KATILMAZ; yoksa oda tablosu bozulurdu.
        ctx.getSessions().put(sessionId, new GameSession(previous.getPlayerName(), retry, null));
        ctx.redirect(exchange, "/quiz");
    }

    // ------------------------------------------------------------- ekranlar

    /**
     * Soru ekrani. Hem serbest hem senkron akista kullanilir; fark yalnizca
     * kalan surenin nereden geldigi.
     */
    private String questionScreen(Quiz quiz, int remaining, int limit,
                                  int questionNumber, int total) {
        Question question = quiz.currentQuestion();
        String[] options = question.getOptions();

        StringBuilder choices = new StringBuilder();
        for (int i = 0; i < options.length; i++) {
            choices.append("        <label class=\"choice\">")
                   .append("<input type=\"radio\" name=\"cevap\" value=\"").append(i).append("\" required>")
                   .append("<span data-key=\"").append(Html.letter(i)).append("\">")
                   .append(Html.escape(options[i])).append("</span></label>\n");
        }

        int progress = Math.round((questionNumber - 1) * 100f / total);

        return """
                <div class="screen">
                  <div class="topbar">
                    <div class="bar"><i style="width:%d%%"></i></div>
                    <div class="clock" id="saat">%d</div>
                  </div>
                  <div class="bar time" id="cubuk" style="margin-bottom:26px">
                    <i id="dolgu" style="width:%d%%"></i>
                  </div>

                  <p class="eyebrow">%s · Soru %d / %d</p>
                  <h2>%s</h2>

                  <form method="POST" action="/cevap" id="cevapForm">
                    <div class="choices">
                %s        </div>
                    <div class="actions">
                      <button class="btn" type="submit">Cevapla</button>
                    </div>
                  </form>
                </div>
                <script>
                  (function () {
                    var toplam = %d, kalan = %d;
                    var saat = document.getElementById('saat');
                    var dolgu = document.getElementById('dolgu');
                    var cubuk = document.getElementById('cubuk');
                    var form = document.getElementById('cevapForm');
                    var sayac = setInterval(function () {
                      kalan--;
                      saat.textContent = kalan < 0 ? 0 : kalan;
                      dolgu.style.width = Math.max(0, kalan / toplam * 100) + '%%';
                      if (kalan <= 5) { cubuk.classList.add('hurry'); saat.classList.add('hurry'); }
                      if (kalan <= 0) {
                        clearInterval(sayac);
                        form.submit();   // sure doldu, bos gonder
                      }
                    }, 1000);
                  })();
                </script>
                """.formatted(
                progress, remaining,
                Math.round(remaining * 100f / limit),
                Html.escape(question.getCategory()),
                questionNumber, total,
                Html.escape(question.getText()),
                choices,
                limit, remaining);
    }

    /** Cevap sonrasi ekrani: dogru sik yesil, secilen yanlis sik kirmizi. */
    private String reviewScreen(GameSession session) {
        GameSession.Feedback fb = session.getFeedback();
        Question question = fb.question();
        String[] options = question.getOptions();

        StringBuilder choices = new StringBuilder();
        for (int i = 0; i < options.length; i++) {
            String state;
            if (question.isCorrect(i)) {
                state = " is-right";
            } else if (i == fb.chosenIndex()) {
                state = " is-wrong";
            } else {
                state = " is-dim";
            }
            choices.append("      <div class=\"choice").append(state).append("\">")
                   .append("<span data-key=\"").append(Html.letter(i)).append("\">")
                   .append(Html.escape(options[i])).append("</span></div>\n");
        }

        String title;
        if (fb.timedOut()) {
            title = "Süre doldu";
        } else if (fb.correct()) {
            title = "Doğru!";
        } else {
            title = "Yanlış";
        }

        String gain = fb.correct() ? "+" + fb.earnedPoints() + " puan" : "+0 puan";
        String why = question.hasExplanation()
                ? "      <div class=\"why\">" + Html.escape(question.getExplanation()) + "</div>\n"
                : "";

        return """
                <div class="screen">
                  <p class="eyebrow">%s</p>
                  <h2>%s</h2>

                  <div class="choices">
                %s      </div>

                  <div class="verdict %s">
                    <h3><span>%s</span><span class="gain">%s</span></h3>
                %s      </div>

                  <div class="actions">
                    <a class="btn %s" href="/devam">Devam</a>
                  </div>
                </div>
                """.formatted(
                Html.escape(question.getCategory()),
                Html.escape(question.getText()),
                choices,
                fb.correct() ? "ok" : "bad",
                title, gain, why,
                fb.correct() ? "" : "blue");
    }

    /** Senkron odadaki oyuncunun gordugu ekran; odanin durumuna gore degisir. */
    private void sendSyncScreen(HttpExchange exchange, GameSession session, Room room)
            throws IOException {
        Quiz quiz = session.getQuiz();
        int total = room.questionCount(ctx.getAllQuestions());
        int playerIndex = quiz.getQuestionNumber() - 1;

        switch (room.getPhase()) {
            case LOBI -> sendWaiting(exchange, "Hazır ol",
                    Html.escape(session.getPlayerName()) + ", odadasın. Hoca başlatınca ilk soru gelecek.",
                    room.playerCount() + " kişi bekliyor");

            case SORU -> {
                if (playerIndex > room.getIndex() || !quiz.hasNext()) {
                    sendWaiting(exchange, "Cevabın alındı",
                            "Diğerlerini bekliyoruz. Hoca cevabı açınca doğrusunu göreceksin.",
                            "Soru " + (room.getIndex() + 1) + " / " + total);
                } else {
                    session.clearFeedback();
                    ctx.sendHtml(exchange, 200, Html.page("Soru " + (room.getIndex() + 1),
                            questionScreen(quiz, room.remainingSeconds(),
                                    quiz.getTimeLimitSeconds(), room.getIndex() + 1, total),
                            ""));
                }
            }

            case CEVAP -> ctx.sendHtml(exchange, 200, Html.page("Cevap",
                    syncReviewScreen(session, room, total),
                    "  <meta http-equiv=\"refresh\" content=\"2\">\n"));

            default -> ctx.redirect(exchange, "/sonuc");
        }
    }

    /** Bekleme ekrani: kendi kendine yenilenir. */
    private void sendWaiting(HttpExchange exchange, String title, String message, String meta)
            throws IOException {
        String body = """
                <div class="screen">
                  <div class="body-area center" style="display:flex;flex-direction:column;
                       justify-content:center;flex:1">
                    <p class="eyebrow center">%s</p>
                    <h1 style="margin-bottom:14px">%s</h1>
                    <p class="muted">%s</p>
                    <div class="pulse"></div>
                  </div>
                </div>
                """.formatted(Html.escape(meta), Html.escape(title), Html.escape(message));
        ctx.sendHtml(exchange, 200, Html.page(title, body,
                "  <meta http-equiv=\"refresh\" content=\"2\">\n"));
    }

    /** Senkron odada cevap acildiginda gosterilen ekran. */
    private String syncReviewScreen(GameSession session, Room room, int total) {
        Question question = room.currentQuestion(ctx.getAllQuestions());
        Quiz quiz = session.getQuiz();

        List<Quiz.AnswerResult> history = quiz.getHistory();
        Quiz.AnswerResult mine = history.isEmpty() ? null : history.get(history.size() - 1);
        boolean correct = mine != null && mine.correct();

        StringBuilder choices = new StringBuilder();
        if (question != null) {
            String[] options = question.getOptions();
            for (int i = 0; i < options.length; i++) {
                String state = question.isCorrect(i) ? " is-right" : " is-dim";
                choices.append("      <div class=\"choice").append(state).append("\">")
                       .append("<span data-key=\"").append(Html.letter(i)).append("\">")
                       .append(Html.escape(options[i])).append("</span></div>\n");
            }
        }

        StringBuilder board = new StringBuilder();
        int rank = 1;
        for (GameSession player : room.standings()) {
            if (rank > 5) {
                break;
            }
            boolean me = player == session;
            board.append("      <div class=\"row").append(me ? " me" : "").append("\">")
                 .append("<span class=\"pos\">").append(rank++).append("</span>")
                 .append("<span class=\"who\">").append(Html.escape(player.getPlayerName())).append("</span>")
                 .append("<span class=\"pts\">").append(player.getQuiz().getPoints()).append("</span>")
                 .append("</div>\n");
        }

        String why = question != null && question.hasExplanation()
                ? "      <div class=\"why\">" + Html.escape(question.getExplanation()) + "</div>\n"
                : "";

        return """
                <div class="screen">
                  <p class="eyebrow">Soru %d / %d · cevap</p>
                  <h2>%s</h2>

                  <div class="choices">
                %s      </div>

                  <div class="verdict %s">
                    <h3><span>%s</span><span class="gain">%s</span></h3>
                %s      </div>

                  <div class="rank">
                %s      </div>
                  <p class="muted small center" style="margin-top:16px">Hoca sonraki soruya geçince devam edeceğiz.</p>
                </div>
                """.formatted(
                room.getIndex() + 1, total,
                question == null ? "" : Html.escape(question.getText()),
                choices,
                correct ? "ok" : "bad",
                correct ? "Doğru!" : "Yanlış",
                mine == null ? "" : "+" + mine.earnedPoints() + " puan",
                why,
                board);
    }

    /**
     * Bir onceki turda cevaplanmis TUM sorular, cevaplanma sirasiyla.
     * /tekrar yanlis yoksa buradan "ayni testi tekrar coz" turu kurar.
     */
    private static List<Question> allAnsweredQuestions(Quiz quiz) {
        List<Question> all = new ArrayList<>();
        for (Quiz.AnswerResult result : quiz.getHistory()) {
            all.add(result.question());
        }
        return all;
    }

    /**
     * Kategori dokumu: her kategoride kac dogru/toplam ve yuzde, yatay
     * cubukla. Tek kategorili bir testte ust bilgideki skor zaten yeterli
     * oldugundan bu blok bos doner.
     */
    private static String categoryBreakdown(Quiz quiz) {
        Map<String, int[]> tally = new LinkedHashMap<>();   // kategori -> [dogru, toplam]
        for (String category : quiz.getCategories()) {
            tally.put(category, new int[2]);
        }
        for (Quiz.AnswerResult result : quiz.getHistory()) {
            int[] counts = tally.computeIfAbsent(result.question().getCategory(), k -> new int[2]);
            counts[1]++;
            if (result.correct()) {
                counts[0]++;
            }
        }
        if (tally.size() <= 1) {
            return "";
        }

        String weakest = null;
        int weakestPercent = 101;
        for (Map.Entry<String, int[]> entry : tally.entrySet()) {
            int[] counts = entry.getValue();
            if (counts[1] == 0) {
                continue;
            }
            int percent = Math.round(counts[0] * 100f / counts[1]);
            if (percent < weakestPercent) {
                weakestPercent = percent;
                weakest = entry.getKey();
            }
        }

        StringBuilder rows = new StringBuilder();
        for (Map.Entry<String, int[]> entry : tally.entrySet()) {
            int[] counts = entry.getValue();
            if (counts[1] == 0) {
                continue;
            }
            int percent = Math.round(counts[0] * 100f / counts[1]);
            boolean isWeakest = entry.getKey().equals(weakest) && percent < 100;
            rows.append("        <div class=\"catrow\">\n")
                .append("          <div class=\"catrow-head\"><span class=\"catname\">")
                .append(Html.escape(entry.getKey()))
                .append("</span><span class=\"catfrac\">")
                .append(counts[0]).append("/").append(counts[1])
                .append(" · %").append(percent)
                .append("</span></div>\n")
                .append("          <div class=\"catbar").append(isWeakest ? " weak" : "")
                .append("\"><i style=\"width:").append(percent).append("%\"></i></div>\n")
                .append("        </div>\n");
        }

        String note = "";
        if (weakest != null && weakestPercent < 100) {
            note = "      <p class=\"catnote\">En çok zorlandığın konu: <b>"
                    + Html.escape(weakest) + "</b> (%" + weakestPercent + ")</p>\n";
        }

        return """
                      <p class="eyebrow" style="margin-top:26px">Konu dökümü</p>
                      <div class="catlist">
                %s      </div>
                %s""".formatted(rows, note);
    }

    /** Ortalama cevap suresi ve en hizli dogru cevabin suresi. */
    private static String speedSummary(Quiz quiz) {
        List<Quiz.AnswerResult> history = quiz.getHistory();
        if (history.isEmpty()) {
            return "";
        }

        long totalMs = 0;
        long fastestCorrectMs = -1;
        for (Quiz.AnswerResult result : history) {
            totalMs += result.elapsedMillis();
            if (result.correct() && (fastestCorrectMs < 0 || result.elapsedMillis() < fastestCorrectMs)) {
                fastestCorrectMs = result.elapsedMillis();
            }
        }

        String avg = formatSeconds(totalMs / history.size());
        String fastest = fastestCorrectMs < 0 ? "—" : formatSeconds(fastestCorrectMs);

        return """
                      <p class="eyebrow" style="margin-top:26px">Hız</p>
                      <div class="stats">
                        <div class="stat"><b>%s</b><span>Ortalama süre</span></div>
                        <div class="stat"><b>%s</b><span>En hızlı doğru</span></div>
                      </div>
                """.formatted(avg, fastest);
    }

    /** Milisaniyeyi "8.3 sn" gibi bir metne cevirir. */
    private static String formatSeconds(long millis) {
        double seconds = Math.round(millis / 100.0) / 10.0;
        String number = seconds == Math.rint(seconds)
                ? String.valueOf((long) seconds)
                : String.valueOf(seconds);
        return number + " sn";
    }

    /**
     * Yanlis yapilan her sorunun kalip okunacak gozden gecirmesi: soru,
     * isaretlenen sik, dogru sik, aciklama. Yanlis yoksa bos doner.
     */
    private String wrongReview(Quiz quiz) {
        List<Quiz.AnswerResult> history = quiz.getHistory();
        List<Integer> chosen = chosenAnswers.getOrDefault(quiz, List.of());

        StringBuilder cards = new StringBuilder();
        int wrongCount = 0;
        for (int i = 0; i < history.size(); i++) {
            Quiz.AnswerResult result = history.get(i);
            if (result.correct()) {
                continue;
            }
            wrongCount++;

            Question question = result.question();
            String[] options = question.getOptions();
            int chosenIndex = i < chosen.size() ? chosen.get(i) : -1;

            String yourAnswer = (chosenIndex < 0 || chosenIndex >= options.length)
                    ? "Süre doldu, cevap verilmedi"
                    : Html.escape(options[chosenIndex]);

            String why = question.hasExplanation()
                    ? "        <p class=\"why\">" + Html.escape(question.getExplanation()) + "</p>\n"
                    : "";

            cards.append("""
                        <div class="card wrongcard">
                          <p class="eyebrow" style="margin-bottom:6px">%s</p>
                          <p class="wrongq">%s</p>
                          <p class="ans wrong">Senin cevabın: <b>%s</b></p>
                          <p class="ans right">Doğru cevap: <b>%s</b></p>
                    %s    </div>
                    """.formatted(
                    Html.escape(question.getCategory()),
                    Html.escape(question.getText()),
                    yourAnswer,
                    Html.escape(question.getCorrectOption()),
                    why));
        }

        if (wrongCount == 0) {
            return "";
        }

        return """
                      <details class="revlist" open>
                        <summary>Yanlış yaptığın %d soru</summary>
                %s      </details>
                """.formatted(wrongCount, cards);
    }

    /** Yanlis varsa "tekrar coz" butonu; yoksa ayni testi tekrar baslat. */
    private static String retryButton(Quiz quiz) {
        int wrong = quiz.getWrongQuestions().size();
        if (wrong == 0) {
            return "    <a class=\"btn\" href=\"/tekrar\">Aynı testi tekrar çöz</a>\n";
        }
        return "    <a class=\"btn\" href=\"/tekrar\">" + wrong
              + " yanlışını şimdi tekrar dene</a>\n";
    }

    private static String verdictTitle(int percentage) {
        if (percentage == 100) return "Kusursuz!";
        if (percentage >= 80)  return "Çok iyi";
        if (percentage >= 50)  return "Fena değil";
        return "Bir tur daha?";
    }
}
