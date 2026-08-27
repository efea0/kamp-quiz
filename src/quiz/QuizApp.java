package quiz;

import quiz.cli.ConsoleUI;
import quiz.core.QuestionBank;
import quiz.core.Quiz;
import quiz.core.Scoreboard;
import quiz.model.Question;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 * Programin giris noktasi.
 * Gorevi: parcalari birbirine baglamak. Kendi basina is mantigi barindirmaz.
 */
public class QuizApp {

    private static final Path QUESTIONS_DIR = Path.of("questions");
    private static final Path SCORES_FILE = Path.of("scores.txt");

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        ConsoleUI ui = new ConsoleUI(scanner);

        ui.printBanner();

        // 1) Sorulari yukle
        List<Question> allQuestions;
        try {
            allQuestions = QuestionBank.loadFromDirectory(QUESTIONS_DIR);
        } catch (IOException e) {
            System.out.println("Sorular yüklenemedi: " + e.getMessage());
            System.out.println("Programı projenin ana klasöründen çalıştırdığından emin ol.");
            return;
        }

        if (allQuestions.isEmpty()) {
            System.out.println("Hiç soru bulunamadı. questions/ klasörüne bir .txt paketi ekle.");
            return;
        }

        System.out.println(allQuestions.size() + " soru yüklendi.");
        System.out.println();

        // 2) Oyuncu adi
        String playerName = ui.askText("Adın nedir? (boş bırak = Misafir): ", "Misafir");

        // 3) Kategori secimi
        List<String> categories = categoriesOf(allQuestions);
        System.out.println();
        System.out.println("Kategoriler:");
        System.out.println("   0) Hepsi karışık");
        for (int i = 0; i < categories.size(); i++) {
            System.out.println("   " + (i + 1) + ") " + categories.get(i));
        }
        int categoryChoice = ui.askNumber("Seçimin: ", 0, categories.size());

        List<Question> selected = (categoryChoice == 0)
                ? allQuestions
                : filterByCategory(allQuestions, categories.get(categoryChoice - 1));

        // 4) Kac soru sorulsun?
        System.out.println();
        int questionCount = ui.askNumber(
                "Kaç soru sorulsun? (1-" + selected.size() + "): ", 1, selected.size());

        // 5) Quiz'i kur ve oynat
        Quiz quiz = new Quiz(selected);
        quiz.shuffle();
        quiz.limitTo(questionCount);

        System.out.println();
        ui.play(quiz);
        ui.printResult(quiz, playerName);

        // 6) Skoru kaydet ve lider tablosunu goster
        Scoreboard scoreboard = new Scoreboard(SCORES_FILE);
        try {
            scoreboard.save(playerName, quiz.getScore(), quiz.getTotal());
            ui.printLeaderboard(scoreboard.topScores(5));
        } catch (IOException e) {
            System.out.println("Skor kaydedilemedi: " + e.getMessage());
        }

        System.out.println();
        System.out.println("Tekrar oynamak için: ./run.sh");
        scanner.close();
    }

    /** Sorularda gecen kategorileri, tekrarsiz ve gorulme sirasiyla verir. */
    private static List<String> categoriesOf(List<Question> questions) {
        Set<String> unique = new LinkedHashSet<>();
        for (Question q : questions) {
            unique.add(q.getCategory());
        }
        return new ArrayList<>(unique);
    }

    private static List<Question> filterByCategory(List<Question> questions, String category) {
        List<Question> result = new ArrayList<>();
        for (Question q : questions) {
            if (q.getCategory().equals(category)) {
                result.add(q);
            }
        }
        return result;
    }
}
