package quiz.web;

import com.sun.net.httpserver.HttpExchange;
import quiz.core.Quiz;
import quiz.model.Question;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hocanin sonuclari Excel'e aktarabilmesi icin CSV disa aktarma uclari.
 *
 *   /disaktar/oda      Bir odanin oyuncu sonuclari (puana gore sirali)
 *   /disaktar/sorular  Soru bazli yanlis analizi (en cok yanlis basta)
 *
 * RFC 4180 kurallarina uyulur: virgul/tirnak/satir-sonu iceren alanlar
 * cift tirnaga alinir, satirlar \r\n ile biter. Excel Turkce karakterleri
 * dogru gostersin diye dosyanin basina UTF-8 BOM konur.
 */
public final class ExportPages {

    private static final String CRLF = "\r\n";
    /** Dosyanin ilk baytlari: Excel'e "bu UTF-8" der. */
    private static final String BOM = "﻿";

    private final ServerContext ctx;

    public ExportPages(ServerContext ctx) {
        this.ctx = ctx;
    }

    /** Bir odanin oyuncu sonuclarini CSV olarak indirir. */
    public void handleRoomCsv(HttpExchange exchange) throws IOException {
        Room room = ctx.getRooms().get(ServerContext.query(exchange, "kod"));
        if (room == null) {
            sendNotFound(exchange);
            return;
        }

        StringBuilder csv = new StringBuilder(BOM);
        writeRow(csv, "sira", "oyuncu", "puan", "dogru", "toplam", "yuzde");

        int rank = 1;
        for (GameSession player : room.standings()) {
            Quiz quiz = player.getQuiz();
            writeRow(csv,
                    String.valueOf(rank++),
                    player.getPlayerName(),
                    String.valueOf(quiz.getPoints()),
                    String.valueOf(quiz.getScore()),
                    String.valueOf(quiz.getTotal()),
                    String.valueOf(quiz.getPercentage()));
        }

        sendCsv(exchange, csv.toString(), "oda-" + safeName(room.getCode()) + "-sonuclar.csv");
    }

    /** Soru bazli yanlis analizini CSV olarak indirir; en cok yanlis basta. */
    public void handleQuestionsCsv(HttpExchange exchange) throws IOException {
        Room room = ctx.getRooms().get(ServerContext.query(exchange, "kod"));
        if (room == null) {
            sendNotFound(exchange);
            return;
        }

        // Soru metni -> [soruldu, yanlis]  +  temsil eden soru nesnesi
        // (RoomPages.handleReport ile ayni hesap; /rapor sayfasinin CSV karsiligi)
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

        StringBuilder csv = new StringBuilder(BOM);
        writeRow(csv, "soru", "kategori", "dogru_cevap", "soruldu", "yanlis", "yanlis_yuzdesi");

        for (Map.Entry<String, int[]> row : rows) {
            int asked = row.getValue()[0];
            int wrong = row.getValue()[1];
            int percent = Math.round(wrong * 100f / asked);
            Question question = byText.get(row.getKey());

            writeRow(csv,
                    question.getText(),
                    question.getCategory(),
                    question.getCorrectOption(),
                    String.valueOf(asked),
                    String.valueOf(wrong),
                    String.valueOf(percent));
        }

        sendCsv(exchange, csv.toString(), "oda-" + safeName(room.getCode()) + "-sorular.csv");
    }

    // ------------------------------------------------------------------ csv

    /** Alanlari virgulle ayirip satiri \r\n ile kapatir. */
    private static void writeRow(StringBuilder csv, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(csvField(fields[i]));
        }
        csv.append(CRLF);
    }

    /**
     * RFC 4180: alanda virgul, cift tirnak ya da satir sonu varsa alan cift
     * tirnaga alinir; icindeki cift tirnaklar ikiye katlanir.
     */
    private static String csvField(String raw) {
        String value = raw == null ? "" : raw;
        boolean needsQuoting = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        if (!needsQuoting) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /** Dosya adinda sadece guvenli karakterler kalsin diye. */
    private static String safeName(String raw) {
        String cleaned = raw.replaceAll("[^A-Za-z0-9-]", "");
        return cleaned.isEmpty() ? "oda" : cleaned;
    }

    // ------------------------------------------------------------------ http

    private void sendCsv(HttpExchange exchange, String csv, String filename) throws IOException {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
        exchange.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"" + filename + "\"");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Oda bulunamadi; HomePages'teki /katil 404'uyla ayni bicim. */
    private void sendNotFound(HttpExchange exchange) throws IOException {
        ctx.sendHtml(exchange, 404, Html.page("Oda bulunamadı", """
                <div class="screen">
                  <div class="card center">
                    <h1>Oda bulunamadı</h1>
                    <p class="muted">Kodu kontrol et. Oda kapanmış da olabilir.</p>
                  </div>
                  <div class="actions"><a class="btn" href="/">Geri dön</a></div>
                </div>
                """));
    }
}
