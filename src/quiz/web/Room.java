package quiz.web;

import quiz.core.Quiz;
import quiz.core.QuizSet;
import quiz.model.Question;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bir oyun odasi: hoca acar, katilimcilar 4 haneli kodla girer.
 *
 * Oda ayni testi paylasan oyuncular kumesidir. Herkesin sorulari
 * ayni SETTEN gelir ama secim ve sira kisiye ozeldir; boylece
 * yan yana oturanlar birbirinin ekranindan kopyalayamaz.
 */
class Room {

    private final String code;
    private final QuizSet set;
    private final long createdAt = System.currentTimeMillis();

    /**
     * Katilimcilar. Ayni anda birden fazla istek listeyi degistirebilecegi icin
     * es zamanli erisime uygun liste kullaniyoruz.
     */
    private final List<GameSession> players = new CopyOnWriteArrayList<>();

    Room(String code, QuizSet set) {
        this.code = code;
        this.set = set;
    }

    String getCode() {
        return code;
    }

    QuizSet getSet() {
        return set;
    }

    long getCreatedAt() {
        return createdAt;
    }

    /** Bu odaya giren her oyuncuya kendi sorulariyla yeni bir quiz uretir. */
    Quiz newQuiz(List<Question> allQuestions) {
        Quiz quiz = new Quiz(set.build(allQuestions));
        quiz.shuffle();
        quiz.setTimeLimitSeconds(set.getTimeLimitSeconds());
        return quiz;
    }

    void addPlayer(GameSession session) {
        players.add(session);
    }

    int playerCount() {
        return players.size();
    }

    /** Puana gore sirali katilimci listesi; projeksiyon ekrani bunu gosterir. */
    List<GameSession> standings() {
        List<GameSession> sorted = new ArrayList<>(players);
        sorted.sort(Comparator
                .comparingInt((GameSession s) -> s.getQuiz().getPoints()).reversed()
                .thenComparing(s -> s.getPlayerName()));
        return sorted;
    }

    /** Herkes testi bitirdi mi? */
    boolean everyoneFinished() {
        if (players.isEmpty()) {
            return false;
        }
        for (GameSession session : players) {
            if (session.getQuiz().hasNext()) {
                return false;
            }
        }
        return true;
    }
}
