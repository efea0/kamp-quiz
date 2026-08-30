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
import java.util.concurrent.atomic.AtomicInteger;

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


    private volatile Phase phase = Phase.LOBI;
    private volatile int index = 0;
    private volatile long questionStartedAt = 0;
    private volatile boolean currentAnswersCommitted;
    private final long createdAt = System.currentTimeMillis();


    private volatile List<Question> sharedQuestions;


    private final Set<String> takenNames = ConcurrentHashMap.newKeySet();



    private final List<GameSession> players = new CopyOnWriteArrayList<>();




    private final AtomicInteger screenTick = new AtomicInteger();


    private volatile List<String> lastScreenOrder = List.of();

    Room(String code, QuizSet set, Mode mode, boolean sharedOrder) {
        this.code = code;
        this.set = set;
        this.mode = mode;

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
        currentAnswersCommitted = false;
        phase = Phase.SORU;
        questionStartedAt = System.currentTimeMillis();
    }


    synchronized void submitSyncAnswer(GameSession player, int answerIndex) {
        if (phase != Phase.SORU || currentAnswersCommitted || !players.contains(player)
                || player.getQuiz().getQuestionNumber() - 1 != index) {
            return;
        }
        Quiz.AnswerResult answer = player.getQuiz().previewAnswer(answerIndex);
        if (!player.setPendingAnswer(answer)) {
            return;
        }
        if (allCurrentAnswersReceived()) {
            commitCurrentAnswers();
        }
    }

    private boolean allCurrentAnswersReceived() {
        if (players.isEmpty()) {
            return false;
        }
        for (GameSession player : players) {
            if (!player.hasPendingAnswer()) {
                return false;
            }
        }
        return true;
    }


    private void commitCurrentAnswers() {
        if (currentAnswersCommitted) {
            return;
        }
        for (GameSession player : players) {
            Quiz quiz = player.getQuiz();
            Quiz.AnswerResult answer = player.takePendingAnswer();
            if (answer == null && quiz.hasNext() && quiz.getQuestionNumber() - 1 == index) {
                answer = quiz.previewAnswer(-1);
            }
            if (answer != null) {
                quiz.commitAnswer(answer);
            }
        }
        currentAnswersCommitted = true;
    }



    synchronized void reveal() {
        if (phase != Phase.SORU) {
            return;
        }
        commitCurrentAnswers();
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
            currentAnswersCommitted = false;
            phase = Phase.SORU;
            questionStartedAt = System.currentTimeMillis();
        }
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


    int currentAnswerCount() {
        if (currentAnswersCommitted) {
            return players.size();
        }
        int count = 0;
        for (GameSession player : players) {
            if (player.hasPendingAnswer()) {
                count++;
            }
        }
        return count;
    }


    record RankClimb(String name, int gain) {
    }



    synchronized RankClimb climbSinceLastScreen(List<GameSession> standings) {
        List<String> currentOrder = new ArrayList<>();
        for (GameSession player : standings) {
            currentOrder.add(player.getPlayerName());
        }

        String climber = null;
        int bestGain = 0;
        for (int i = 0; i < currentOrder.size(); i++) {
            int oldPos = lastScreenOrder.indexOf(currentOrder.get(i));
            if (oldPos < 0) {
                continue;
            }
            int gain = oldPos - i;
            if (gain > bestGain) {
                bestGain = gain;
                climber = currentOrder.get(i);
            }
        }
        lastScreenOrder = currentOrder;


        return bestGain >= 2 ? new RankClimb(climber, bestGain) : null;
    }


    int nextScreenTick() {
        return screenTick.getAndIncrement();
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
