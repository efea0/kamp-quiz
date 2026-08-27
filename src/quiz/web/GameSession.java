package quiz.web;

import quiz.core.Quiz;

/**
 * Tek bir oyuncunun web oturumu.
 * Sunucu ayni anda birden fazla oyuncuya hizmet verdigi icin,
 * her oyuncunun kendi Quiz nesnesi ve kendi ilerlemesi olmalidir.
 */
class GameSession {

    private final String playerName;
    private final Quiz quiz;

    /** Bir onceki cevabin sonucu; bir sonraki sayfada gosterilip temizlenir. */
    private String feedbackMessage;
    private boolean feedbackCorrect;
    private boolean scoreSaved;

    GameSession(String playerName, Quiz quiz) {
        this.playerName = playerName;
        this.quiz = quiz;
    }

    String getPlayerName() {
        return playerName;
    }

    Quiz getQuiz() {
        return quiz;
    }

    void setFeedback(boolean correct, String message) {
        this.feedbackCorrect = correct;
        this.feedbackMessage = message;
    }

    /** Geri bildirimi bir kez okur ve siler; sayfa yenilenince tekrar gorunmesin. */
    String consumeFeedback() {
        if (feedbackMessage == null) {
            return "";
        }
        String css = feedbackCorrect ? "ok" : "bad";
        String html = "    <div class=\"feedback " + css + "\">" + Html.escape(feedbackMessage) + "</div>\n";
        feedbackMessage = null;
        return html;
    }

    boolean isScoreSaved() {
        return scoreSaved;
    }

    void markScoreSaved() {
        scoreSaved = true;
    }
}
