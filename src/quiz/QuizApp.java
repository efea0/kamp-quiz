package quiz;

import quiz.cli.ConsoleUI;
import quiz.core.QuestionBank;
import quiz.core.Quiz;
import quiz.core.QuizSet;
import quiz.core.QuizSetLoader;
import quiz.core.Scoreboard;
import quiz.web.WebServer;
import quiz.model.Question;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Programin giris noktasi.
 * Gorevi: parcalari birbirine baglamak. Kendi basina is mantigi barindirmaz.
 */
public class QuizApp {

    private static final Path QUESTIONS_DIR = Path.of("questions");
    private static final Path SETS_DIR = Path.of("sets");
    private static final Path SCORES_FILE = Path.of("scores.txt");

    private static final int DEFAULT_PORT = 8080;

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

        // 2) Web modu mu, konsol modu mu?
        //    ./run.sh web        -> tarayicidan oynanir
        //    ./run.sh web 9000   -> baska port
        if (args.length > 0 && args[0].equalsIgnoreCase("web")) {
            int port = args.length > 1 ? parsePort(args[1]) : DEFAULT_PORT;
            try {
                List<QuizSet> sets = QuizSetLoader.loadFromDirectory(SETS_DIR);
                new WebServer(allQuestions, sets, new Scoreboard(SCORES_FILE), port).start();
            } catch (IOException e) {
                System.out.println("Sunucu başlatılamadı: " + e.getMessage());
                System.out.println("Port " + port + " başka bir program tarafından kullanılıyor olabilir.");
                System.out.println("Farklı bir port dene:  ./run.sh web 9000");
            }
            return;   // sunucu arka planda calismaya devam eder
        }

        // 3) Konsol modu: oyuncu adi
        String playerName = ui.askText("Adın nedir? (boş bırak = Misafir): ", "Misafir");

        // 3) Kategori secimi
        List<String> categories = QuestionBank.categoriesOf(allQuestions);
        System.out.println();
        System.out.println("Kategoriler:");
        System.out.println("   0) Hepsi karışık");
        for (int i = 0; i < categories.size(); i++) {
            System.out.println("   " + (i + 1) + ") " + categories.get(i));
        }
        int categoryChoice = ui.askNumber("Seçimin: ", 0, categories.size());

        List<Question> selected = (categoryChoice == 0)
                ? allQuestions
                : QuestionBank.byCategory(allQuestions, categories.get(categoryChoice - 1));

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
            scoreboard.save(playerName, quiz.getScore(), quiz.getTotal(), quiz.getPoints());
            ui.printLeaderboard(scoreboard.topScores(5));
        } catch (IOException e) {
            System.out.println("Skor kaydedilemedi: " + e.getMessage());
        }

        System.out.println();
        System.out.println("Tekrar oynamak için: ./run.sh");
        scanner.close();
    }

    /** Komut satirindan gelen port degerini dogrular. */
    private static int parsePort(String text) {
        try {
            int port = Integer.parseInt(text.trim());
            return (port >= 1024 && port <= 65535) ? port : DEFAULT_PORT;
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }
}
