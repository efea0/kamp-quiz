package quiz.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Tek bir quiz sorusunu temsil eder.
 *
 * Bu sinif bir "model" sinifidir: hicbir is yapmaz, sadece veriyi tasir
 * ve o verinin bozulmasini engeller.
 */
public class Question {

    /**
     * Sorunun zorluk seviyesi. Belirtilmemisse ORTA varsayilir.
     */
    public enum Difficulty {
        KOLAY, ORTA, ZOR;

        /**
         * "kolay" / "orta" / "zor" kelimelerinden birini zorluk degerine cevirir.
         * Buyuk/kucuk harf duyarsizdir. Taniyamazsa bos doner; boylece cagiran
         * taraf bu metni baska bir amacla (ornegin dogru cevap sayisi) kullanip
         * kullanmadigina kendisi karar verir.
         */
        public static Optional<Difficulty> fromText(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "kolay" -> Optional.of(KOLAY);
                case "orta" -> Optional.of(ORTA);
                case "zor" -> Optional.of(ZOR);
                default -> Optional.empty();
            };
        }
    }

    private final String text;          // sorunun metni
    private final String[] options;     // siklar
    private final int correctIndex;     // dogru sikkin sirasi (0'dan baslar)
    private final String category;      // hangi paketten geldigi
    private final String explanation;   // "neden bu cevap" - bos olabilir
    private final Difficulty difficulty; // zorluk seviyesi - belirtilmemisse ORTA

    /**
     * Zorluk belirtilmeden soru olusturur; zorluk ORTA kabul edilir.
     * Eski cagrilarin bozulmamasi icin korunuyor.
     */
    public Question(String text, String[] options, int correctIndex,
                    String category, String explanation) {
        this(text, options, correctIndex, category, explanation, Difficulty.ORTA);
    }

    public Question(String text, String[] options, int correctIndex,
                    String category, String explanation, Difficulty difficulty) {
        // --- BEKCI KONTROLLERI ---
        // Bozuk bir soru asla dogamaz. Hata, quiz calisirken degil,
        // soru olusturulurken patlar. Boylece hatayi nerede yaptigimizi biliriz.
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Soru metni bos olamaz.");
        }
        if (options == null || options.length < 2) {
            throw new IllegalArgumentException("Bir soruda en az 2 sik olmali.");
        }
        if (correctIndex < 0 || correctIndex >= options.length) {
            throw new IllegalArgumentException(
                    "Dogru cevap numarasi sik araliginin disinda: " + (correctIndex + 1));
        }

        this.text = text;
        this.options = options.clone();   // savunma amacli kopya (asagida anlatiliyor)
        this.correctIndex = correctIndex;
        this.category = category == null ? "genel" : category;
        this.explanation = explanation == null ? "" : explanation.trim();
        this.difficulty = difficulty == null ? Difficulty.ORTA : difficulty;
    }

    public String getText() {
        return text;
    }

    /** Siklarin bir KOPYASINI verir; disaridan degistirilemesin diye. */
    public String[] getOptions() {
        return options.clone();
    }

    public int getOptionCount() {
        return options.length;
    }

    public String getCategory() {
        return category;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    /** Dogru cevabi disari vermeden "bu cevap dogru mu?" sorusunu yanitlar. */
    public boolean isCorrect(int answerIndex) {
        return answerIndex == correctIndex;
    }

    /** Sadece cevap verildikten SONRA, geri bildirim gostermek icin kullanilir. */
    public String getCorrectOption() {
        return options[correctIndex];
    }

    /** Cevabin nedeni. Bos olabilir; o zaman gosterilmez. */
    public String getExplanation() {
        return explanation;
    }

    public boolean hasExplanation() {
        return !explanation.isEmpty();
    }
}
