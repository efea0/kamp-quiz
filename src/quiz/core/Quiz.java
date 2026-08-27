package quiz.core;

import quiz.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bir quiz oturumu: soru listesini tutar, sirayi ilerletir, skoru sayar.
 *
 * Bu sinif EKRANI HIC BILMEZ. System.out.println burada gecmez.
 * Kural: is mantigi (core) ile ekran (cli) birbirinden ayridir.
 * Boylece ayni Quiz sinifini yarin web sunucusunda da kullanabiliriz.
 */
public class Quiz {

    private final List<Question> questions;
    private int currentIndex = 0;   // su an kacinci sorudayiz
    private int score = 0;          // kac dogru yaptik

    public Quiz(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("Quiz en az 1 soru icermeli.");
        }
        this.questions = new ArrayList<>(questions);   // kendi kopyamiz
    }

    /** Sorulari karistirir; her oyunda sira farkli olsun diye. */
    public void shuffle() {
        Collections.shuffle(questions);
    }

    /** Quiz'i en fazla 'max' soruyla sinirlar. */
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

    /**
     * Cevabi isler: dogruysa skoru artirir, her durumda bir sonraki soruya gecer.
     * @return cevap dogruysa true
     */
    public boolean submitAnswer(int answerIndex) {
        boolean correct = currentQuestion().isCorrect(answerIndex);
        if (correct) {
            score++;
        }
        currentIndex++;
        return correct;
    }

    /** Kacinci sorudayiz (insan sayimiyla: 1, 2, 3...). */
    public int getQuestionNumber() {
        return currentIndex + 1;
    }

    public int getScore() {
        return score;
    }

    public int getTotal() {
        return questions.size();
    }

    public int getPercentage() {
        return Math.round(score * 100f / questions.size());
    }

    /** Bu quizde hangi kategoriler var? */
    public Set<String> getCategories() {
        Set<String> categories = new LinkedHashSet<>();
        for (Question q : questions) {
            categories.add(q.getCategory());
        }
        return categories;
    }
}
