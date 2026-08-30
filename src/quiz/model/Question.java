package quiz.model;

import java.util.Locale;
import java.util.Optional;

public class Question {



    public enum Difficulty {
        KOLAY, ORTA, ZOR;



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

    private final String text;
    private final String[] options;
    private final int correctIndex;
    private final String category;
    private final String explanation;
    private final Difficulty difficulty;



    public Question(String text, String[] options, int correctIndex,
                    String category, String explanation) {
        this(text, options, correctIndex, category, explanation, Difficulty.ORTA);
    }

    public Question(String text, String[] options, int correctIndex,
                    String category, String explanation, Difficulty difficulty) {



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
        this.options = options.clone();
        this.correctIndex = correctIndex;
        this.category = category == null ? "genel" : category;
        this.explanation = explanation == null ? "" : explanation.trim();
        this.difficulty = difficulty == null ? Difficulty.ORTA : difficulty;
    }

    public String getText() {
        return text;
    }


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


    public boolean isCorrect(int answerIndex) {
        return answerIndex == correctIndex;
    }


    public String getCorrectOption() {
        return options[correctIndex];
    }


    public String getExplanation() {
        return explanation;
    }

    public boolean hasExplanation() {
        return !explanation.isEmpty();
    }
}
