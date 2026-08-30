package quiz.core;

import quiz.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Quiz {


    private static final int BASE_POINTS = 500;

    private static final int MAX_SPEED_BONUS = 500;
    private static final int DEFAULT_TIME_LIMIT_SECONDS = 20;



    public record AnswerResult(boolean correct, boolean timedOut,
                               int earnedPoints, long elapsedMillis, Question question) {
    }

    private final List<Question> questions;
    private int currentIndex = 0;
    private int score = 0;
    private int points = 0;

    private int timeLimitSeconds = DEFAULT_TIME_LIMIT_SECONDS;
    private long questionStartedAt = 0;



    private final List<AnswerResult> history = new ArrayList<>();

    public Quiz(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("Quiz en az 1 soru icermeli.");
        }
        this.questions = new ArrayList<>(questions);
    }


    public void shuffle() {
        Collections.shuffle(questions);
    }


    public void limitTo(int max) {
        if (max > 0 && max < questions.size()) {
            questions.subList(max, questions.size()).clear();
        }
    }

    public boolean hasNext() {
        return currentIndex < questions.size();
    }

    public Question currentQuestion() {
        return questions.get(currentIndex);
    }



    public void startQuestionTimer() {
        if (questionStartedAt == 0) {
            questionStartedAt = System.currentTimeMillis();
        }
    }


    public long elapsedMillis() {
        return questionStartedAt == 0 ? 0 : System.currentTimeMillis() - questionStartedAt;
    }


    public int remainingSeconds() {
        long left = timeLimitSeconds * 1000L - elapsedMillis();
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public void setTimeLimitSeconds(int seconds) {
        if (seconds > 0) {
            this.timeLimitSeconds = seconds;
        }
    }



    public synchronized AnswerResult previewAnswer(int answerIndex) {
        Question question = currentQuestion();
        long elapsed = elapsedMillis();
        boolean timedOut = questionStartedAt != 0 && elapsed > timeLimitSeconds * 1000L;
        boolean correct = !timedOut && question.isCorrect(answerIndex);
        int earned = correct ? BASE_POINTS + speedBonus(elapsed) : 0;
        return new AnswerResult(correct, timedOut, earned, elapsed, question);
    }


    public synchronized void commitAnswer(AnswerResult result) {
        if (result == null || !hasNext() || result.question() != currentQuestion()) {
            throw new IllegalArgumentException("Cevap mevcut soruya ait degil.");
        }
        if (result.correct()) {
            score++;
            points += result.earnedPoints();
        }
        currentIndex++;
        questionStartedAt = 0;
        history.add(result);
    }



    public synchronized AnswerResult submitAnswer(int answerIndex) {
        AnswerResult result = previewAnswer(answerIndex);
        commitAnswer(result);
        return result;
    }


    private int speedBonus(long elapsedMillis) {
        double limit = timeLimitSeconds * 1000.0;
        double remainingRatio = 1.0 - (elapsedMillis / limit);
        if (remainingRatio < 0) {
            remainingRatio = 0;
        }
        return (int) Math.round(MAX_SPEED_BONUS * remainingRatio);
    }


    public int getQuestionNumber() {
        return currentIndex + 1;
    }

    public int getScore() {
        return score;
    }

    public int getPoints() {
        return points;
    }


    public int getMaxPoints() {
        return questions.size() * (BASE_POINTS + MAX_SPEED_BONUS);
    }

    public int getTotal() {
        return questions.size();
    }

    public int getPercentage() {
        return Math.round(score * 100f / questions.size());
    }


    public List<AnswerResult> getHistory() {
        return List.copyOf(history);
    }


    public List<Question> getWrongQuestions() {
        List<Question> wrong = new ArrayList<>();
        for (AnswerResult result : history) {
            if (!result.correct()) {
                wrong.add(result.question());
            }
        }
        return wrong;
    }


    public Set<String> getCategories() {
        Set<String> categories = new LinkedHashSet<>();
        for (Question q : questions) {
            categories.add(q.getCategory());
        }
        return categories;
    }
}
