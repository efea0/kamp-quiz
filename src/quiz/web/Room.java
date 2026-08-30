package quiz.web;

import quiz.core.Quiz;
import quiz.core.QuizSet;
import quiz.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    enum Mode {
        SERBEST,
        SENKRON
    }

    enum Phase {
        LOBI,
        SORU,
        CEVAP,
        BITTI
    }

    private final String code;
    private final QuizSet set;
    private final Mode mode;
    private final boolean sharedOrder;
    private final boolean reactionsEnabled;
    private final String hostToken;

    private volatile Phase phase = Phase.LOBI;
    private volatile int index = 0;
    private volatile long questionStartedAt = 0;
    private final long createdAt = System.currentTimeMillis();

    // Paylasik sirada listeyi ilk oyuncu uretir ve herkes ayni listeyi kullanir.
    private volatile List<Question> sharedQuestions;

    private final Set<String> takenNames = ConcurrentHashMap.newKeySet();

    // Es zamanli istekler listeyi degistirebildigi icin kopyalama gerektirmeyen liste.
    private final List<GameSession> players = new CopyOnWriteArrayList<>();

    record FastestAnswer(GameSession player, long elapsedMillis) {
    }

    private final Map<Integer, FastestAnswer> fastestCorrect = new ConcurrentHashMap<>();
    private volatile boolean lastRevealTimedOut;

    Room(String code, QuizSet set, Mode mode, boolean sharedOrder) {
        this(code, set, mode, sharedOrder, true, UUID.randomUUID().toString());
    }

    Room(String code, QuizSet set, Mode mode, boolean sharedOrder, boolean reactionsEnabled) {
        this(code, set, mode, sharedOrder, reactionsEnabled, UUID.randomUUID().toString());
    }

    Room(String code, QuizSet set, Mode mode, boolean sharedOrder,
         boolean reactionsEnabled, String hostToken) {
        this.code = code;
        this.set = set;
        this.mode = mode;
        this.reactionsEnabled = reactionsEnabled;
        this.hostToken = hostToken;
        // Senkron akista herkes ayni soruyu gormek ZORUNDA; secim yok.
        this.sharedOrder = mode == Mode.SENKRON || sharedOrder;
    }

    boolean isSharedOrder() {
        return sharedOrder;
    }

    Mode getMode() {
        return mode;
    }

    boolean isSynchronous() {
        return mode == Mode.SENKRON;
    }

    boolean reactionsEnabled() {
        return reactionsEnabled;
    }

    boolean isHostToken(String candidate) {
        return candidate != null && hostToken.equals(candidate);
    }

    Phase getPhase() {
        return phase;
    }

    int getIndex() {
        return index;
    }

    Question currentQuestion(List<Question> allQuestions) {
        List<Question> questions = questionList(allQuestions);
        return index < questions.size() ? questions.get(index) : null;
    }

    int questionCount(List<Question> allQuestions) {
        return questionList(allQuestions).size();
    }

    int remainingSeconds() {
        if (questionStartedAt == 0) {
            return set.getTimeLimitSeconds();
        }
        long left = set.getTimeLimitSeconds() * 1000L - (System.currentTimeMillis() - questionStartedAt);
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    synchronized void start(List<Question> allQuestions) {
        if (phase != Phase.LOBI) {
            return;
        }
        questionList(allQuestions);
        index = 0;
        phase = Phase.SORU;
        questionStartedAt = System.currentTimeMillis();
        for (GameSession player : players) {
            player.getQuiz().startQuestionTimerAt(questionStartedAt);
        }
    }

    // Cevap vermeyenler yanlis sayilir; yoksa siradan kopup kalirlar.
    synchronized void reveal() {
        reveal(false);
    }

    synchronized void expireIfNeeded() {
        if (phase == Phase.SORU && remainingSeconds() == 0) {
            reveal(true);
        }
    }

    synchronized boolean lastRevealTimedOut() {
        return lastRevealTimedOut;
    }

    private synchronized void reveal(boolean timedOut) {
        if (phase != Phase.SORU) {
            return;
        }
        lastRevealTimedOut = timedOut;
        for (GameSession player : players) {
            Quiz quiz = player.getQuiz();
            while (quiz.hasNext() && quiz.getQuestionNumber() - 1 <= index) {
                quiz.startQuestionTimerAt(questionStartedAt);
                quiz.submitAnswer(-1);
                player.clearFeedback();
            }
        }
        phase = Phase.CEVAP;
    }

    synchronized void next(List<Question> allQuestions) {
        if (phase != Phase.CEVAP) {
            return;
        }
        index++;
        if (index >= questionList(allQuestions).size()) {
            phase = Phase.BITTI;
        } else {
            phase = Phase.SORU;
            lastRevealTimedOut = false;
            questionStartedAt = System.currentTimeMillis();
            for (GameSession player : players) {
                player.getQuiz().startQuestionTimerAt(questionStartedAt);
            }
        }
    }

    synchronized void recordCorrectAnswer(GameSession player, int questionIndex) {
        if (!reactionsEnabled || questionIndex != index || phase != Phase.SORU) {
            return;
        }
        long elapsed = Math.max(0, System.currentTimeMillis() - questionStartedAt);
        FastestAnswer previous = fastestCorrect.get(questionIndex);
        if (previous == null || elapsed < previous.elapsedMillis()) {
            fastestCorrect.put(questionIndex, new FastestAnswer(player, elapsed));
        }
    }

    FastestAnswer fastestCorrect(int questionIndex) {
        return fastestCorrect.get(questionIndex);
    }

    private List<Question> questionList(List<Question> allQuestions) {
        if (sharedQuestions == null) {
            synchronized (this) {
                if (sharedQuestions == null) {
                    List<Question> built = set.build(allQuestions);
                    Collections.shuffle(built);
                    sharedQuestions = List.copyOf(built);
                }
            }
        }
        return sharedQuestions;
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
        List<Question> questions = sharedOrder ? questionList(allQuestions) : set.build(allQuestions);

        Quiz quiz = new Quiz(questions);
        if (!sharedOrder) {
            quiz.shuffle();
        }
        quiz.setTimeLimitSeconds(set.getTimeLimitSeconds());
        return quiz;
    }

    boolean isNameTaken(String name) {
        return takenNames.contains(normalize(name));
    }

    boolean reserveName(String name) {
        return takenNames.add(normalize(name));
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(java.util.Locale.forLanguageTag("tr"));
    }

    void addPlayer(GameSession session) {
        players.add(session);
        if (phase == Phase.SORU) {
            session.getQuiz().startQuestionTimerAt(questionStartedAt);
        }
    }

    int playerCount() {
        return players.size();
    }

    List<GameSession> standings() {
        List<GameSession> sorted = new ArrayList<>(players);
        sorted.sort(Comparator
                .comparingInt((GameSession s) -> s.getQuiz().getPoints()).reversed()
                .thenComparing(s -> s.getPlayerName()));
        return sorted;
    }

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
