package quiz.test;

import quiz.core.QuestionBank;
import quiz.core.Quiz;
import quiz.core.QuizSet;
import quiz.core.QuizSetLoader;
import quiz.model.Question;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Projenin kendi kendini sinamasi.  Calistirmak icin:  ./run.sh test
 *
 * Dis test kutuphanesi (JUnit) kullanmiyoruz cunku projenin kurali
 * sifir bagimlilik. Yaptigi is basit: bir sey bekledigimiz gibi degilse
 * ekrana yazar ve program hata koduyla biter.
 *
 * Katkida bulunuyorsan, gonderdigin degisiklikten SONRA bunu calistir.
 */
public class SelfTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("Kendi kendini test\n");

        List<Question> questions = QuestionBank.loadFromDirectory(Path.of("questions"));
        List<QuizSet> sets = QuizSetLoader.loadFromDirectory(Path.of("sets"));

        testQuestionsLoad(questions);
        testQuestionGuards();
        testDifficultyDefault();
        testDifficultyLineParsing();
        testDifficultyFileHeader();
        testScoring();
        testTimerDoesNotRestart();
        testRetryList();
        testSets(sets, questions);
        testDifficultyFilteredSet(questions);
        testCoreDoesNotPrint();

        System.out.println();
        System.out.println("Geçen: " + passed + "   Kalan: " + failed);
        if (failed > 0) {
            System.out.println("\nBir şeyler bozulmuş. Yukarıdaki satırlara bak.");
            System.exit(1);
        }
        System.out.println("Her şey yolunda.");
    }

    // ------------------------------------------------------------ testler

    private static void testQuestionsLoad(List<Question> questions) {
        check("Soru paketleri yükleniyor", !questions.isEmpty());

        int withoutExplanation = 0;
        for (Question q : questions) {
            check("Soruda en az 2 şık var: " + kisa(q.getText()), q.getOptionCount() >= 2);
            check("Doğru cevap gerçekten şıklardan biri: " + kisa(q.getText()),
                    q.getCorrectOption() != null && !q.getCorrectOption().isBlank());
            if (!q.hasExplanation()) {
                withoutExplanation++;
            }
        }
        System.out.println("  bilgi: " + questions.size() + " sorunun "
                + (questions.size() - withoutExplanation) + " tanesinde açıklama var");
    }

    private static void testQuestionGuards() {
        check("Boş soru metni reddediliyor",
                throwsError(() -> new Question("", new String[]{"a", "b"}, 0, "t", "")));
        check("Tek şıklı soru reddediliyor",
                throwsError(() -> new Question("Soru?", new String[]{"a"}, 0, "t", "")));
        check("Aralık dışı doğru cevap reddediliyor",
                throwsError(() -> new Question("Soru?", new String[]{"a", "b"}, 5, "t", "")));

        // Kapsulleme: disaridan alinan dizi degistirilse bile soru bozulmamali
        String[] options = {"doğru", "yanlış"};
        Question question = new Question("Soru?", options, 0, "t", "");
        options[0] = "BOZULDU";
        check("Şıklar dışarıdan değiştirilemiyor", "doğru".equals(question.getCorrectOption()));

        String[] copy = question.getOptions();
        copy[0] = "YİNE BOZULDU";
        check("Getter kopya veriyor", "doğru".equals(question.getCorrectOption()));
    }

    private static void testScoring() {
        Question q1 = new Question("1+1?", new String[]{"2", "3"}, 0, "t", "");
        Question q2 = new Question("2+2?", new String[]{"4", "5"}, 0, "t", "");
        Quiz quiz = new Quiz(List.of(q1, q2));

        check("Başlangıçta skor sıfır", quiz.getScore() == 0 && quiz.getPoints() == 0);
        check("Soru sayısı doğru", quiz.getTotal() == 2);

        quiz.startQuestionTimer();
        Quiz.AnswerResult first = quiz.submitAnswer(0);
        check("Doğru cevap doğru sayılıyor", first.correct());
        check("Doğru cevap puan kazandırıyor", first.earnedPoints() > 0);
        check("Hızlı cevap taban puanın üstünde", first.earnedPoints() > 500);

        quiz.startQuestionTimer();
        Quiz.AnswerResult second = quiz.submitAnswer(1);
        check("Yanlış cevap yanlış sayılıyor", !second.correct());
        check("Yanlış cevap puan getirmiyor", second.earnedPoints() == 0);

        check("Skor 1/2", quiz.getScore() == 1);
        check("Yüzde 50", quiz.getPercentage() == 50);
        check("Quiz bitti", !quiz.hasNext());
        check("Geçmişte 2 kayıt var", quiz.getHistory().size() == 2);
    }

    /**
     * Sayfa yenilenince sure sifirlanmamali; yoksa ogrenci soruyu
     * defalarca yenileyip sinirsiz sure kazanirdi.
     */
    private static void testTimerDoesNotRestart() throws InterruptedException {
        Quiz quiz = new Quiz(List.of(
                new Question("A?", new String[]{"1", "2"}, 0, "t", "")));

        quiz.startQuestionTimer();
        Thread.sleep(60);
        long first = quiz.elapsedMillis();

        quiz.startQuestionTimer();   // sayfa yenilendi
        long second = quiz.elapsedMillis();

        check("Sayaç ikinci çağrıda sıfırlanmıyor", second >= first);
        check("Geçen süre gerçekten ilerliyor", first >= 50);
    }

    private static void testRetryList() {
        Question q1 = new Question("A?", new String[]{"1", "2"}, 0, "t", "");
        Question q2 = new Question("B?", new String[]{"1", "2"}, 0, "t", "");
        Quiz quiz = new Quiz(List.of(q1, q2));

        quiz.startQuestionTimer();
        quiz.submitAnswer(0);              // doğru
        quiz.startQuestionTimer();
        quiz.submitAnswer(1);              // yanlış

        List<Question> wrong = quiz.getWrongQuestions();
        check("Tekrar listesinde sadece yanlışlar var", wrong.size() == 1);
        check("Tekrar listesindeki soru doğru soru",
                !wrong.isEmpty() && wrong.get(0).getText().equals("B?"));
    }

    private static void testSets(List<QuizSet> sets, List<Question> questions) {
        check("Hazır testler yükleniyor", !sets.isEmpty());

        List<String> categories = QuestionBank.categoriesOf(questions);
        for (QuizSet set : sets) {
            for (String category : set.getCategoryCounts().keySet()) {
                check("'" + set.getName() + "' setindeki kategori var: " + category,
                        categories.contains(category));
            }
            List<Question> built = set.build(questions);
            check("'" + set.getName() + "' istenen sayıda soru üretiyor",
                    built.size() == set.totalQuestions());
        }
    }

    /**
     * Mimarinin tek kurali: core ve model paketleri EKRANI BILMEZ.
     *
     * Bu denetim kaynak kodun kendisini okur. Kural bir kez yazilip
     * unutulmasin diye; birisi core icine System.out yazarsa test kirmizi olur.
     */
    private static void testCoreDoesNotPrint() throws IOException {
        for (String pkg : new String[]{"core", "model"}) {
            Path dir = Path.of("src", "quiz", pkg);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (var files = Files.list(dir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    check("quiz." + pkg + "/" + file.getFileName() + " ekrana yazmıyor",
                            !printsToScreen(file));
                }
            }
        }
    }

    /** Yorum satirlarini atlayarak gercek bir System.out cagrisi var mi bakar. */
    private static boolean printsToScreen(Path file) throws IOException {
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) {
                continue;   // yorum; kural bu satirda anlatiliyor olabilir
            }
            if (line.contains("System.out") || line.contains("System.err")) {
                return true;
            }
        }
        return false;
    }

    // --------------------------------------------------------- yardimcilar

    private static void check(String what, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("  KALDI: " + what);
        }
    }

    private static boolean throwsError(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static String kisa(String text) {
        return text.length() > 40 ? text.substring(0, 40) + "..." : text;
    }
}
