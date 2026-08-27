package quiz.cli;

import quiz.core.Quiz;
import quiz.core.Scoreboard;
import quiz.model.Question;

import java.util.List;
import java.util.Scanner;

/**
 * Konsol arayuzu: ekrana basma ve klavyeden okuma isleri BURADA toplanir.
 * Quiz mantigi burada yoktur; o quiz.core paketinde.
 */
public class ConsoleUI {

    private final Scanner scanner;

    public ConsoleUI(Scanner scanner) {
        this.scanner = scanner;
    }

    public void printBanner() {
        System.out.println();
        System.out.println("=========================================");
        System.out.println("         KAMP QUIZ MOTORU  v1.0          ");
        System.out.println("=========================================");
        System.out.println();
    }

    /** Bos gecilmeyen bir metin sorar. */
    public String askText(String prompt, String defaultValue) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            System.out.println();
            return defaultValue;   // girdi akisi bitti (or. dosyadan besleme)
        }
        String input = scanner.nextLine().trim();
        return input.isEmpty() ? defaultValue : input;
    }

    /** min-max araliginda bir sayi sorar; yanlis girisi affeder ve tekrar sorar. */
    public int askNumber(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            if (!scanner.hasNextLine()) {
                System.out.println();
                return min;   // girdi akisi bitti, guvenli varsayilan
            }
            String input = scanner.nextLine().trim();

            try {
                int value = Integer.parseInt(input);
                if (value < min || value > max) {
                    System.out.println("  -> Lütfen " + min + " ile " + max + " arasında bir sayı gir.");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                System.out.println("  -> '" + input + "' bir sayı değil. Tekrar dene.");
            }
        }
    }

    /** Quiz'i bastan sona oynatir. */
    public void play(Quiz quiz) {
        System.out.println("Başlıyoruz! Toplam " + quiz.getTotal() + " soru.");
        System.out.println("-----------------------------------------");

        while (quiz.hasNext()) {
            Question question = quiz.currentQuestion();

            System.out.println();
            System.out.println("Soru " + quiz.getQuestionNumber() + "/" + quiz.getTotal()
                    + "   [" + question.getCategory() + "]");
            System.out.println(question.getText());
            System.out.println();

            String[] options = question.getOptions();
            for (int i = 0; i < options.length; i++) {
                System.out.println("   " + (i + 1) + ") " + options[i]);
            }
            System.out.println();

            int humanAnswer = askNumber("Cevabın (1-" + options.length + "): ", 1, options.length);

            // Insan 1'den sayar, dizi 0'dan -> cevir
            boolean correct = quiz.submitAnswer(humanAnswer - 1);

            if (correct) {
                System.out.println("  [+] DOĞRU!");
            } else {
                System.out.println("  [-] Yanlış. Doğru cevap: " + question.getCorrectOption());
            }
        }
    }

    public void printResult(Quiz quiz, String playerName) {
        int percentage = quiz.getPercentage();

        System.out.println();
        System.out.println("=========================================");
        System.out.println("  SONUÇ - " + playerName);
        System.out.println("  Skor: " + quiz.getScore() + "/" + quiz.getTotal()
                + "  (%" + percentage + ")");
        System.out.println("  " + comment(percentage));
        System.out.println("=========================================");
    }

    private String comment(int percentage) {
        if (percentage == 100) return "Kusursuz! Hocaya meydan okuyabilirsin.";
        if (percentage >= 80)  return "Çok iyi, neredeyse tamam.";
        if (percentage >= 50)  return "Fena değil, biraz daha çalışma.";
        return "Bir tur daha denemeye ne dersin?";
    }

    public void printLeaderboard(List<Scoreboard.Entry> entries) {
        System.out.println();
        System.out.println("*** LIDER TABLOSU ***");
        System.out.println("-----------------------------------------");

        if (entries.isEmpty()) {
            System.out.println("  Henüz kayıt yok. İlk sen ol!");
            return;
        }

        int rank = 1;
        for (Scoreboard.Entry e : entries) {
            System.out.printf("  %d. %-15s %2d/%-2d  %%%-3d  %s%n",
                    rank++, e.name(), e.score(), e.total(), e.percentage(), e.date());
        }
    }
}
