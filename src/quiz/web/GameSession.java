package quiz.web;

import quiz.core.Quiz;
import quiz.model.Question;

/**
 * Tek bir oyuncunun web oturumu.
 * Sunucu ayni anda birden fazla oyuncuya hizmet verdigi icin,
 * her oyuncunun kendi Quiz nesnesi ve kendi ilerlemesi olmalidir.
 */
class GameSession {

    /**
     * Cevaplanmis bir sorunun sonucu.
     * Cevap ekraninda soruyu ve siklari tekrar gosterebilmek icin
     * sorunun kendisini de tasir.
     */
    record Feedback(boolean correct, boolean timedOut, int earnedPoints,
                    Question question, int chosenIndex) {
    }

    private final String playerName;
    private final Quiz quiz;

    /** Cevap verildikten sonra gosterilecek sonuc; "Devam" ile temizlenir. */
    private Feedback feedback;
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

    Feedback getFeedback() {
        return feedback;
    }

    void setFeedback(Feedback feedback) {
        this.feedback = feedback;
    }

    void clearFeedback() {
        this.feedback = null;
    }

    boolean isScoreSaved() {
        return scoreSaved;
    }

    void markScoreSaved() {
        scoreSaved = true;
    }
}
