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
    private final String roomCode;   // oda disinda oynayanlarda null

    /** Cevap verildikten sonra gosterilecek sonuc; "Devam" ile temizlenir. */
    private Feedback feedback;
    private boolean scoreSaved;

    GameSession(String playerName, Quiz quiz, String roomCode) {
        this.playerName = playerName;
        this.quiz = quiz;
        this.roomCode = roomCode;
    }

    String getPlayerName() {
        return playerName;
    }

    String getRoomCode() {
        return roomCode;
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
