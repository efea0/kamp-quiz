/**
 * Tek bir quiz sorusunu temsil eden kalip (sinif).
 * Bir sorunun uc parcasi vardir: metni, siklari ve dogru sikkin numarasi.
 */
public class Question {

    // --- ALANLAR (fields): Her sorunun tasidigi bilgiler ---
    private final String text;          // sorunun metni
    private final String[] options;     // siklar: {"a sikki", "b sikki", ...}
    private final int correctIndex;     // dogru sikkin sira numarasi (0'dan baslar)

    // --- CONSTRUCTOR: Yeni bir soru nesnesi dogarken calisir ---
    public Question(String text, String[] options, int correctIndex) {
        this.text = text;
        this.options = options;
        this.correctIndex = correctIndex;
    }

    // --- GETTER'LAR: Disaridan bilgiyi okuma kapilari ---
    public String getText() {
        return text;
    }

    public String[] getOptions() {
        return options;
    }

    /**
     * Verilen cevap dogru mu?
     * @param answerIndex kullanicinin sectigi sikkin numarasi (0'dan baslar)
     * @return dogruysa true, yanlissa false
     */
    public boolean isCorrect(int answerIndex) {
        return answerIndex == correctIndex;
    }
}
