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
    private String feedbackExplanation;
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

    void setFeedback(boolean correct, String message, String explanation) {
        this.feedbackCorrect = correct;
        this.feedbackMessage = message;
        this.feedbackExplanation = explanation;
    }

    /** Geri bildirimi bir kez okur ve siler; sayfa yenilenince tekrar gorunmesin. */
    String consumeFeedback() {
        if (feedbackMessage == null) {
            return "";
        }
        String css = feedbackCorrect ? "ok" : "bad";
        StringBuilder html = new StringBuilder();
        html.append("    <div class=\"feedback ").append(css).append("\">")
            .append(Html.escape(feedbackMessage));
        if (feedbackExplanation != null && !feedbackExplanation.isEmpty()) {
            html.append("<div class=\"why\">").append(Html.escape(feedbackExplanation)).append("</div>");
        }
        html.append("</div>\n");
        feedbackMessage = null;
        feedbackExplanation = null;
        return html.toString();
    }

    boolean isScoreSaved() {
        return scoreSaved;
    }

    void markScoreSaved() {
        scoreSaved = true;
    }
}
