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

    /** Dogru cevabin taban puani. */
    private static final int BASE_POINTS = 500;
    /** Hizli cevaba verilen en fazla ek puan. */
    private static final int MAX_SPEED_BONUS = 500;
    private static final int DEFAULT_TIME_LIMIT_SECONDS = 20;

    /**
     * Bir sorunun cevaplanma sonucu.
     * Arayuzun ekrana basmak icin ihtiyac duydugu her seyi tek pakette dondurur.
     */
    public record AnswerResult(boolean correct, boolean timedOut,
                               int earnedPoints, long elapsedMillis, Question question) {
    }

    private final List<Question> questions;
    private int currentIndex = 0;   // su an kacinci sorudayiz
    private int score = 0;          // kac dogru yaptik
    private int points = 0;         // hiz bonuslu toplam puan

    private int timeLimitSeconds = DEFAULT_TIME_LIMIT_SECONDS;
    private long questionStartedAt = 0;   // 0 = sayac baslatilmadi

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
     * Soru ekrana gelince cagrilir; sure buradan itibaren sayilir.
     *
     * Ayni soru icin ikinci kez cagrilmasi sayaci SIFIRLAMAZ. Aksi halde
     * oyuncu sayfayi yenileyerek sureyi bastan baslatip her soruda
     * tam hiz bonusu alabilirdi.
     */
    public void startQuestionTimer() {
        if (questionStartedAt == 0) {
            questionStartedAt = System.currentTimeMillis();
        }
    }

    /** Bu soruda ne kadar sure gectigini milisaniye olarak verir. */
    public long elapsedMillis() {
        return questionStartedAt == 0 ? 0 : System.currentTimeMillis() - questionStartedAt;
    }

    /** Bu soruda kac saniye kaldigini verir; sure dolduysa 0. */
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

    /**
     * Cevabi isler: dogruysa skoru ve puani artirir, her durumda sonraki soruya gecer.
     *
     * Puanlama: dogru cevap 500 taban puan alir; ustune kalan sureye orantili
     * en fazla 500 hiz bonusu eklenir. Sure dolduysa cevap yanlis sayilir.
     */
    public AnswerResult submitAnswer(int answerIndex) {
        Question question = currentQuestion();
        long elapsed = elapsedMillis();

        boolean timedOut = questionStartedAt != 0 && elapsed > timeLimitSeconds * 1000L;
        boolean correct = !timedOut && question.isCorrect(answerIndex);

        int earned = 0;
        if (correct) {
            score++;
            earned = BASE_POINTS + speedBonus(elapsed);
            points += earned;
        }

        currentIndex++;
        questionStartedAt = 0;
        return new AnswerResult(correct, timedOut, earned, elapsed, question);
    }

    /** Ne kadar hizli cevaplandiysa o kadar cok bonus. */
    private int speedBonus(long elapsedMillis) {
        double limit = timeLimitSeconds * 1000.0;
        double remainingRatio = 1.0 - (elapsedMillis / limit);
        if (remainingRatio < 0) {
            remainingRatio = 0;
        }
        return (int) Math.round(MAX_SPEED_BONUS * remainingRatio);
    }

    /** Kacinci sorudayiz (insan sayimiyla: 1, 2, 3...). */
    public int getQuestionNumber() {
        return currentIndex + 1;
    }

    public int getScore() {
        return score;
    }

    public int getPoints() {
        return points;
    }

    /** Tam performansta toplanabilecek en yuksek puan. */
    public int getMaxPoints() {
        return questions.size() * (BASE_POINTS + MAX_SPEED_BONUS);
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
