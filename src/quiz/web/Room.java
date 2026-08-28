package quiz.web;

import quiz.core.Quiz;
import quiz.core.QuizSet;
import quiz.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bir oyun odasi: hoca acar, katilimcilar 4 haneli kodla girer.
 *
 * Oda ayni testi paylasan oyuncular kumesidir. Soru sirasi iki turlu olabilir:
 *
 *   PAYLASIK (varsayilan)  Herkes ayni sorulari ayni sirada gorur.
 *                          Kahoot/Wayground boyle calisir; perdedeki siralama
 *                          anlamli olur cunku herkes ayni soruda yarisir.
 *
 *   KISIYE OZEL            Her oyuncuya ayri secim ve ayri sira verilir.
 *                          Yan yana oturanlar kopyalayamaz ama siralama
 *                          "kim hangi soruda" bilgisini kaybeder.
 *
 * Hangisinin kullanilacagina odayi kuran karar verir.
 */
class Room {

    private final String code;
    private final QuizSet set;
    private final boolean sharedOrder;
    private final long createdAt = System.currentTimeMillis();

    /** Paylasik sirada herkesin aldigi tek liste; ilk oyuncuda uretilir. */
    private volatile List<Question> sharedQuestions;

    /** Odadaki isimler (kucuk harfe cevrilmis). Ayni isim iki kez giremez. */
    private final Set<String> takenNames = ConcurrentHashMap.newKeySet();

    /**
     * Katilimcilar. Ayni anda birden fazla istek listeyi degistirebilecegi icin
     * es zamanli erisime uygun liste kullaniyoruz.
     */
    private final List<GameSession> players = new CopyOnWriteArrayList<>();

    Room(String code, QuizSet set, boolean sharedOrder) {
        this.code = code;
        this.set = set;
        this.sharedOrder = sharedOrder;
    }

    boolean isSharedOrder() {
        return sharedOrder;
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

    /**
     * Odaya giren oyuncu icin quiz uretir.
     *
     * Paylasik sirada ilk oyuncu listeyi uretir, sonrakiler ayni listeyi alir.
     * Uretimin bir kez olmasi icin senkronize; iki kisi ayni anda katilirsa
     * ikisi de ayni sorulari gormeli.
     */
    Quiz newQuiz(List<Question> allQuestions) {
        List<Question> questions;

        if (sharedOrder) {
            if (sharedQuestions == null) {
                synchronized (this) {
                    if (sharedQuestions == null) {
                        List<Question> built = set.build(allQuestions);
                        Collections.shuffle(built);
                        sharedQuestions = List.copyOf(built);
                    }
                }
            }
            questions = sharedQuestions;
        } else {
            questions = set.build(allQuestions);
        }

        Quiz quiz = new Quiz(questions);
        if (!sharedOrder) {
            quiz.shuffle();
        }
        quiz.setTimeLimitSeconds(set.getTimeLimitSeconds());
        return quiz;
    }

    /** Bu isim odada zaten var mi? */
    boolean isNameTaken(String name) {
        return takenNames.contains(normalize(name));
    }

    /** Ismi rezerve eder; zaten alinmissa false doner. */
    boolean reserveName(String name) {
        return takenNames.add(normalize(name));
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(java.util.Locale.forLanguageTag("tr"));
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
