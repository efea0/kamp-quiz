package quiz.web;

import com.sun.net.httpserver.HttpExchange;
import quiz.core.Quiz;
import quiz.core.QuizSet;
import quiz.model.Question;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RoomPages {

    private final ServerContext ctx;

    public RoomPages(ServerContext ctx) {
        this.ctx = ctx;
    }


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


    public void handleHostPanel(HttpExchange exchange) throws IOException {
        Room room = ctx.getRooms().get(ServerContext.query(exchange, "kod"));
        if (room == null) {
            ctx.redirect(exchange, "/kur");
            return;
        }


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
                ctx.joinQr(exchange, 190, room.getCode()),
                Html.escape(ctx.joinUrl(exchange, room.getCode())),
                room.playerCount(),
                room.getSet().totalQuestions(),
                room.isSharedOrder() ? "herkese aynı" : "kişiye özel",
                list,
                hostControls(room),
                room.getCode(), room.getCode(), room.getCode(), room.getCode());

        ctx.sendHtml(exchange, 200, Html.page("Oda " + room.getCode(), body,
                "  <meta http-equiv=\"refresh\" content=\"4\">\n"));
    }


    public void handleScreen(HttpExchange exchange) throws IOException {
        Room room = ctx.getRooms().get(ServerContext.query(exchange, "kod"));
        if (room == null) {
            ctx.redirect(exchange, "/kur");
            return;
        }

        StringBuilder list = new StringBuilder();
        List<GameSession> standings = room.standings();





        int gosterilecek = Math.min(standings.size(), EKRANDA_GOSTERILEN);

        if (standings.isEmpty()) {
            list.append("      <p class=\"muted center\">Katılımcılar bekleniyor...</p>\n");
        } else {
            for (int i = 0; i < gosterilecek; i++) {
                GameSession player = standings.get(i);
                list.append("      <div class=\"row\"><span class=\"pos\">").append(i + 1)
                    .append("</span><span class=\"who\">").append(Html.escape(player.getPlayerName()))
                    .append("</span><span class=\"sub\">").append(ServerContext.progressLabel(player))
                    .append("</span><span class=\"pts\">").append(player.getQuiz().getPoints())
                    .append("</span></div>\n");
            }
            int kalan = standings.size() - gosterilecek;
            if (kalan > 0) {
                list.append("      <p class=\"muted small center\" style=\"margin:10px 0 0\">")
                    .append("+ ").append(kalan).append(" katılımcı daha</p>\n");
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

                %s
                %s
                %s
                  <div class="rank big">
                %s      </div>

                %s
                  <p class="muted small center" style="margin-top:18px">%d katılımcı · %s</p>
                </div>
                """.formatted(
                Html.escape(room.getSet().getName()),
                room.getCode(),
                ctx.joinQr(exchange, 108, room.getCode()),
                Html.escape(ctx.joinUrl(exchange, room.getCode())),
                projectionQuestionBlock(room),
                projectionDashboard(room),
                reactionsBlock(room, standings),
                list,
                projectionSoundBlock(room),
                room.playerCount(),
                room.everyoneFinished() ? "test bitti" : "devam ediyor");

        ctx.sendHtml(exchange, 200, Html.page("Ekran " + room.getCode(), body,
                "  <meta http-equiv=\"refresh\" content=\"3\">\n"));
    }




    private String projectionDashboard(Room room) {
        boolean synchronous = room.isSynchronous();
        int total = room.questionCount(ctx.getAllQuestions());
        int current = synchronous && room.getPhase() != Room.Phase.LOBI ? room.getIndex() + 1 : 0;
        int remaining = current == 0 ? total : Math.max(0, total - current);
        int seconds = synchronous && room.getPhase() == Room.Phase.SORU
                ? room.remainingSeconds() : 0;
        String phase = switch (room.getPhase()) {
            case LOBI -> "Bekleniyor";
            case SORU -> "Cevaplanıyor";
            case CEVAP -> "Cevap açık";
            case BITTI -> "Tamamlandı";
        };
        String currentLabel = current == 0 ? "—" : current + " / " + total;
        String remainingLabel = synchronous ? String.valueOf(remaining) : "—";
        String secondsLabel = synchronous && room.getPhase() == Room.Phase.SORU
                ? seconds + " sn" : "—";
        String countLabel = synchronous ? room.currentAnswerCount() + " / " + room.playerCount() : "—";

        return """
                <section class="projection-dashboard" aria-label="Quiz durumu">
                  <div class="dash-stat"><span>Durum</span><b>%s</b></div>
                  <div class="dash-stat"><span>Süre</span><b id="projectionTimer">%s</b></div>
                  <div class="dash-stat"><span>Şu anki soru</span><b>%s</b></div>
                  <div class="dash-stat"><span>Kalan soru</span><b>%s</b></div>
                  <div class="dash-stat"><span>Cevaplayan</span><b>%s</b></div>
                </section>
                <script>
                  (function () {
                    var remaining = %d;
                    var timer = document.getElementById('projectionTimer');
                    if (!timer || remaining <= 0) return;
                    window.setInterval(function () {
                      remaining = Math.max(0, remaining - 1);
                      timer.textContent = remaining + ' sn';
                    }, 1000);
                  })();
                </script>
                """.formatted(Html.escape(phase), Html.escape(secondsLabel),
                Html.escape(currentLabel), Html.escape(remainingLabel),
                Html.escape(countLabel), seconds);
    }


    private String projectionSoundBlock(Room room) {
        String soundKey = room.isSynchronous()
                ? "sync:" + room.getPhase() + ":" + room.getIndex()
                : "free:" + room.nextScreenTick();
        return """
                <div class="projection-tools">
                  <button class="sound-toggle" id="sesAc" type="button">🔇 Sesi aç</button>
                </div>
                <script>
                  (function () {
                    var key = "%s";
                    var button = document.getElementById("sesAc");
                    var audio = null;
                    function tone(frequency, duration, delay) {
                      if (!audio) return;
                      var now = audio.currentTime + (delay || 0);
                      var oscillator = audio.createOscillator();
                      var gain = audio.createGain();
                      oscillator.frequency.value = frequency;
                      oscillator.type = "sine";
                      gain.gain.setValueAtTime(0.0001, now);
                      gain.gain.exponentialRampToValueAtTime(0.09, now + 0.015);
                      gain.gain.exponentialRampToValueAtTime(0.0001, now + duration);
                      oscillator.connect(gain).connect(audio.destination);
                      oscillator.start(now);
                      oscillator.stop(now + duration + 0.02);
                    }
                    function activate(playWelcome) {
                      var AudioCtor = window.AudioContext || window.webkitAudioContext;
                      if (!AudioCtor) return;
                      audio = audio || new AudioCtor();
                      audio.resume().then(function () {
                        localStorage.setItem("kampQuizSound", "1");
                        button.classList.add("active");
                        button.textContent = "🔊 Ses açık";
                        if (playWelcome) tone(660, 0.12);
                      });
                    }
                    button.addEventListener("click", function () { activate(true); });
                    if (localStorage.getItem("kampQuizSound") === "1") {
                      button.classList.add("active");
                      button.textContent = "🔊 Ses açık";
                      try {
                        activate(false);
                        var previous = localStorage.getItem("kampQuizLastSound");
                        if (previous && previous !== key) {
                          tone(key.indexOf(":CEVAP:") >= 0 ? 740 : 520, 0.1);
                        }
                        localStorage.setItem("kampQuizLastSound", key);
                      } catch (ignored) { }
                    }
                  })();
                </script>
                """.formatted(Html.escape(soundKey));
    }


    private String projectionQuestionBlock(Room room) {
        if (!room.isSynchronous()) {
            return "";
        }
        if (room.getPhase() == Room.Phase.LOBI) {
            return "      <div class=\"projection-state\">Hoca başlatınca soru burada görünecek.</div>\n";
        }
        if (room.getPhase() == Room.Phase.BITTI) {
            return "";
        }

        Question question = room.currentQuestion(ctx.getAllQuestions());
        if (question == null) {
            return "";
        }

        boolean revealed = room.getPhase() == Room.Phase.CEVAP;
        StringBuilder choices = new StringBuilder();
        String[] options = question.getOptions();
        for (int i = 0; i < options.length; i++) {
            String state = revealed && question.isCorrect(i) ? " correct" : "";
            choices.append("          <div class=\"projection-choice").append(state).append("\">")
                   .append("<b>").append(Html.letter(i)).append("</b>")
                   .append("<span>").append(Html.escape(options[i])).append("</span></div>\n");
        }

        return """
                <section class="projection-question%s">
                  <div class="projection-question-head">
                    <p class="eyebrow">Soru %d / %d</p>
                    <span class="answer-count">%d / %d cevap</span>
                  </div>
                  <h2>%s</h2>
                  <div class="projection-choices">
                %s      </div>
                </section>
                """.formatted(
                revealed ? " revealed" : "",
                room.getIndex() + 1,
                room.questionCount(ctx.getAllQuestions()),
                room.currentAnswerCount(),
                room.playerCount(),
                Html.escape(question.getText()),
                choices);
    }




    private static final int EKRANDA_GOSTERILEN = 8;

    private String reactionsBlock(Room room, List<GameSession> standings) {
        boolean anyHistory = false;
        for (GameSession player : standings) {
            if (!player.getQuiz().getHistory().isEmpty()) {
                anyHistory = true;
                break;
            }
        }
        if (!anyHistory) {
            return "";
        }

        boolean finished = room.everyoneFinished()
                || (room.isSynchronous() && room.getPhase() == Room.Phase.BITTI);

        return finished ? closingSummary(standings) : rotatingReaction(room, standings);
    }


    private String closingSummary(List<GameSession> standings) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        int totalAsked = 0;
        int totalCorrect = 0;
        String fastestName = null;
        long fastestMillis = Long.MAX_VALUE;
        List<String> perfect = new ArrayList<>();

        for (GameSession player : standings) {
            Quiz quiz = player.getQuiz();
            List<Quiz.AnswerResult> history = quiz.getHistory();
            for (Quiz.AnswerResult result : history) {
                totalAsked++;
                String key = result.question().getText();
                int[] tally = counts.computeIfAbsent(key, k -> new int[2]);
                tally[0]++;
                if (result.correct()) {
                    totalCorrect++;
                    if (result.elapsedMillis() < fastestMillis) {
                        fastestMillis = result.elapsedMillis();
                        fastestName = player.getPlayerName();
                    }
                } else {
                    tally[1]++;
                }
            }
            if (!history.isEmpty() && quiz.getScore() == quiz.getTotal()) {
                perfect.add(player.getPlayerName());
            }
        }

        if (totalAsked == 0) {
            return "";
        }

        int average = Math.round(totalCorrect * 100f / totalAsked);

        String hardestText = null;
        int hardestPercent = -1;
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            int asked = entry.getValue()[0];
            int wrong = entry.getValue()[1];
            int percent = Math.round(wrong * 100f / asked);
            if (percent > hardestPercent) {
                hardestPercent = percent;
                hardestText = entry.getKey();
            }
        }

        StringBuilder rows = new StringBuilder();
        rows.append("        <li><span class=\"label\">Sınıf ortalaması</span><b class=\"")
            .append(average >= 60 ? "good" : average < 35 ? "bad" : "info")
            .append("\">%").append(average).append("</b></li>\n");

        if (hardestText != null) {
            rows.append("        <li><span class=\"label\">En çok yanlış</span><b class=\"bad\">")
                .append(Html.escape(kisa(hardestText))).append(" · %").append(hardestPercent)
                .append("</b></li>\n");
        }
        if (fastestName != null) {
            rows.append("        <li><span class=\"label\">En hızlı doğru</span><b class=\"info\">")
                .append(Html.escape(fastestName)).append(" · ")
                .append(seconds(fastestMillis)).append("</b></li>\n");
        }
        if (!perfect.isEmpty()) {
            rows.append("        <li><span class=\"label\">Kusursuz</span><b class=\"good\">")
                .append(Html.escape(String.join(", ", perfect))).append("</b></li>\n");
        }

        return """
                <div class="reaction summary">
                  <p class="tag">Test bitti · özet</p>
                  <ul>
                %s      </ul>
                </div>
                """.formatted(rows);
    }



    private String rotatingReaction(Room room, List<GameSession> standings) {
        List<String> candidates = new ArrayList<>();


        String streakName = null;
        int bestStreak = 0;
        for (GameSession player : standings) {
            List<Quiz.AnswerResult> history = player.getQuiz().getHistory();
            int streak = 0;
            for (int i = history.size() - 1; i >= 0; i--) {
                if (history.get(i).correct()) {
                    streak++;
                } else {
                    break;
                }
            }
            if (streak > bestStreak) {
                bestStreak = streak;
                streakName = player.getPlayerName();
            }
        }
        if (bestStreak >= 3) {
            candidates.add("<b class=\"good\">" + Html.escape(streakName) + "</b> üst üste "
                    + bestStreak + " doğru");
        }


        Map<String, Integer> lastAnsweredCount = new LinkedHashMap<>();
        for (GameSession player : standings) {
            List<Quiz.AnswerResult> history = player.getQuiz().getHistory();
            if (!history.isEmpty()) {
                String key = history.get(history.size() - 1).question().getText();
                lastAnsweredCount.merge(key, 1, Integer::sum);
            }
        }
        String currentQuestion = null;
        int currentQuestionSeen = 0;
        for (Map.Entry<String, Integer> entry : lastAnsweredCount.entrySet()) {
            if (entry.getValue() > currentQuestionSeen) {
                currentQuestionSeen = entry.getValue();
                currentQuestion = entry.getKey();
            }
        }
        if (currentQuestion != null) {
            int asked = 0;
            int correct = 0;
            for (GameSession player : standings) {
                for (Quiz.AnswerResult result : player.getQuiz().getHistory()) {
                    if (result.question().getText().equals(currentQuestion)) {
                        asked++;
                        if (result.correct()) {
                            correct++;
                        }
                    }
                }
            }
            if (asked > 0) {
                candidates.add("Son soruyu " + asked + " kişiden <b class=\""
                        + (correct * 2 >= asked ? "good" : "bad") + "\">" + correct
                        + "</b>'sı bildi");
            }
        }


        String fastestName = null;
        long fastestMillis = Long.MAX_VALUE;
        for (GameSession player : standings) {
            for (Quiz.AnswerResult result : player.getQuiz().getHistory()) {
                if (result.correct() && result.elapsedMillis() < fastestMillis) {
                    fastestMillis = result.elapsedMillis();
                    fastestName = player.getPlayerName();
                }
            }
        }
        if (fastestName != null) {
            candidates.add("En hızlı doğru: <b class=\"info\">" + Html.escape(fastestName)
                    + "</b>, " + seconds(fastestMillis));
        }


        int totalAsked = 0;
        int totalCorrect = 0;
        for (GameSession player : standings) {
            for (Quiz.AnswerResult result : player.getQuiz().getHistory()) {
                totalAsked++;
                if (result.correct()) {
                    totalCorrect++;
                }
            }
        }
        if (totalAsked > 0) {
            int average = Math.round(totalCorrect * 100f / totalAsked);
            candidates.add("Sınıf ortalaması <b class=\""
                    + (average >= 60 ? "good" : average < 35 ? "bad" : "info")
                    + "\">%" + average + "</b>");
        }


        Room.RankClimb climb = room.climbSinceLastScreen(standings);
        if (climb != null) {
            candidates.add("<b class=\"good\">" + Html.escape(climb.name()) + "</b> " + climb.gain()
                    + " sıra yükseldi");
        }


        Map<String, int[]> counts = new LinkedHashMap<>();
        for (GameSession player : standings) {
            for (Quiz.AnswerResult result : player.getQuiz().getHistory()) {
                int[] tally = counts.computeIfAbsent(result.question().getText(), k -> new int[2]);
                tally[0]++;
                if (!result.correct()) {
                    tally[1]++;
                }
            }
        }
        String hardestText = null;
        int hardestPercent = -1;
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            int asked = entry.getValue()[0];
            if (asked < 2) {
                continue;
            }
            int wrong = entry.getValue()[1];
            int percent = Math.round(wrong * 100f / asked);
            if (percent > hardestPercent) {
                hardestPercent = percent;
                hardestText = entry.getKey();
            }
        }
        if (hardestText != null && hardestPercent >= 40) {
            candidates.add("En çok yanlış: <b class=\"bad\">" + Html.escape(kisa(hardestText))
                    + "</b> · %" + hardestPercent);
        }

        if (candidates.isEmpty()) {
            return "";
        }

        int pick = Math.floorMod(room.nextScreenTick(), candidates.size());
        return """
                <div class="reaction">
                  <p class="tag">Canlı tepki</p>
                  <p>%s</p>
                </div>
                """.formatted(candidates.get(pick));
    }


    private static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.1f saniye", millis / 1000.0);
    }


    private static String kisa(String text) {
        return text.length() > 46 ? text.substring(0, 46) + "…" : text;
    }


    public void handleReport(HttpExchange exchange) throws IOException {
        Room room = ctx.getRooms().get(ServerContext.query(exchange, "kod"));
        if (room == null) {
            ctx.redirect(exchange, "/kur");
            return;
        }


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
            return Double.compare(rb, ra);
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
