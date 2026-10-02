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

    private final String sessionId;
    private final String playerName;
    private final String roomCode;   // oda disinda oynayanlarda null

    /**
     * Oyununun anlik durumu. Oda "ikinci tur" baslatildiginda DEGISTIRILIR
     * (bkz. Room.startSecondRound). O yuzden final degil; volatile ki
     * temizlik/yardimci is parcaciklari guncel degeri gorur.
     */
    private volatile Quiz quiz;

    /** Cevap verildikten sonra gosterilecek sonuc; "Devam" ile temizlenir. */
    private Feedback feedback;
    private boolean scoreSaved;

    /**
     * Son istegin geldigi an (epoch ms). Sunucu bunu kullanarak, uzun suredir
     * kimsenin dokunmadigi oturumlari siler. Alan volatile: oturum haritasi
     * eszamanli (ConcurrentHashMap) ve temizlik baska bir is parcacigindan
     * calisiyor; gorunurluk garantisi olmazsa eski deger okunabilir.
     */
    private volatile long lastSeen = System.currentTimeMillis();

    GameSession(String sessionId, String playerName, Quiz quiz, String roomCode) {
        this.sessionId = sessionId;
        this.playerName = playerName;
        this.quiz = quiz;
        this.roomCode = roomCode;
    }

    /** Oturum kimligi; sunucu bunu sessions haritasinin anahtari olarak kullanir. */
    String sessionId() {
        return sessionId;
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

    /** Ayni odada yeni bir tura gecerken cagrilir. */
    void replaceQuiz(Quiz quiz) {
        this.quiz = quiz;
        this.feedback = null;
        this.scoreSaved = false;
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

    /** Her istekte cagrilir; oturumun canli oldugunu belirtir. */
    void touch() {
        lastSeen = System.currentTimeMillis();
    }

    long lastSeen() {
        return lastSeen;
    }
}
