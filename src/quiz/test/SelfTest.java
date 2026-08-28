package quiz.test;

import quiz.ai.QuestionGenerator;
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
        testQuestionGeneratorSurvivesMissingPromptsDir();

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

    private static void testDifficultyDefault() {
        Question normal = new Question("A?", new String[]{"1", "2"}, 0, "t", "");
        check("Zorluk belirtilmeyen soru ORTA kabul ediliyor",
                normal.getDifficulty() == Question.Difficulty.ORTA);

        Question hard = new Question("B?", new String[]{"1", "2"}, 0, "t", "",
                Question.Difficulty.ZOR);
        check("6 parametreli constructor zorluğu doğru kaydediyor",
                hard.getDifficulty() == Question.Difficulty.ZOR);

        Question nullDifficulty = new Question("C?", new String[]{"1", "2"}, 0, "t", "", null);
        check("6 parametreli constructor'a null verilirse ORTA'ya düşüyor",
                nullDifficulty.getDifficulty() == Question.Difficulty.ORTA);
    }

    /** Dosya biçiminde satır sonundaki isteğe bağlı zorluk sütunu doğru okunuyor mu? */
    private static void testDifficultyLineParsing() throws IOException {
        Path file = writeTempQuestionsFile(
                "# baslik: Gecici Zorluk Testi\n"
                + "İki artı iki kaç eder? | 3 | 4 | 5 | 6 | 2 | zor\n"
                + "Bir artı bir kaç eder? | 1 | 2 | 3 | 4 | 2\n");
        try {
            List<Question> loaded = QuestionBank.loadFromFile(file);
            check("Zorluklu ve zorluksuz satır birlikte yükleniyor", loaded.size() == 2);

            Question withDifficulty = loaded.get(0);
            check("Satır sonundaki 'zor' kelimesi doğru okunuyor",
                    withDifficulty.getDifficulty() == Question.Difficulty.ZOR);
            check("Zorluk sütunü varken doğru cevap numarası hâlâ doğru ayrıştırılıyor",
                    "4".equals(withDifficulty.getCorrectOption()));

            Question withoutDifficulty = loaded.get(1);
            check("Zorluk sütunu olmayan eski biçim hâlâ çalışıyor (ORTA varsayılan)",
                    withoutDifficulty.getDifficulty() == Question.Difficulty.ORTA);
            check("Eski biçimde doğru cevap numarası bozulmuyor",
                    "2".equals(withoutDifficulty.getCorrectOption()));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** '# zorluk: ...' dosya başlığı, satırda zorluk belirtilmeyen sorulara uygulanıyor mu? */
    private static void testDifficultyFileHeader() throws IOException {
        Path file = writeTempQuestionsFile(
                "# baslik: Gecici Baslik Testi\n"
                + "# zorluk: kolay\n"
                + "Soru bir? | a | b | 1\n"
                + "Soru iki? | a | b | c | 3 | zor\n");
        try {
            List<Question> loaded = QuestionBank.loadFromFile(file);
            check("'# zorluk:' başlıklı geçici dosya yükleniyor", loaded.size() == 2);

            check("'# zorluk:' başlığı, satırda zorluk belirtilmeyen soruya uygulanıyor",
                    loaded.get(0).getDifficulty() == Question.Difficulty.KOLAY);
            check("Satır sonundaki zorluk, dosya geneli başlığı eziyor",
                    loaded.get(1).getDifficulty() == Question.Difficulty.ZOR);
        } finally {
            Files.deleteIfExists(file);
        }
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

    /** Zorluk süzgeçli bir setin doğru sayıda soru ürettiğini denetler. */
    private static void testDifficultyFilteredSet(List<Question> questions) {
        List<String> categories = QuestionBank.categoriesOf(questions);
        if (!categories.contains("Linux Temelleri")) {
            // questions/linux.txt yoksa (ornegin baska bir ortamda) testi sessizce atla.
            return;
        }

        List<Question> categoryPool = QuestionBank.byCategory(questions, "Linux Temelleri");
        int kolaySayisi = QuestionBank.byDifficulty(categoryPool, Question.Difficulty.KOLAY).size();
        check("Linux Temelleri kategorisinde en az 1 kolay soru var", kolaySayisi >= 1);

        // Tam olarak eldeki kolay soru sayısı kadar istenirse, hepsi kolay gelmeli.
        Map<String, Integer> exactCounts = new LinkedHashMap<>();
        exactCounts.put("Linux Temelleri", kolaySayisi);
        QuizSet exactSet = new QuizSet("Kolay Tur", "", 20, exactCounts, Question.Difficulty.KOLAY);
        List<Question> exactBuilt = exactSet.build(questions);
        check("Zorluk süzgeçli set, yeterli soru varken istenen sayıda üretiyor",
                exactBuilt.size() == kolaySayisi);

        boolean allEasy = true;
        for (Question q : exactBuilt) {
            if (q.getDifficulty() != Question.Difficulty.KOLAY) {
                allEasy = false;
                break;
            }
        }
        check("Yeterli soru varken sadece istenen zorluktan seçiliyor", allEasy);

        // Eldekinden fazlası istenirse, eksik diğer zorluklardan tamamlanmalı (hata firlatmamali).
        int wanted = kolaySayisi + 3;
        Map<String, Integer> overCounts = new LinkedHashMap<>();
        overCounts.put("Linux Temelleri", wanted);
        QuizSet overSet = new QuizSet("Kolay Tur Fazla", "", 20, overCounts, Question.Difficulty.KOLAY);
        List<Question> overBuilt = overSet.build(questions);
        int expected = Math.min(wanted, categoryPool.size());
        check("Zorluk süzgeçli set, eksik kaldığında diğer zorluklardan tamamlıyor",
                overBuilt.size() == expected);

        // Zorluk süzgeci olmayan bir set, eski davranışı aynen korumalı.
        Map<String, Integer> plainCounts = new LinkedHashMap<>();
        plainCounts.put("Linux Temelleri", 5);
        QuizSet plainSet = new QuizSet("Karma Tur", "", 20, plainCounts);
        check("Zorluk süzgeci olmayan set eskisi gibi çalışıyor",
                plainSet.build(questions).size() == 5 && !plainSet.hasDifficultyFilter());
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

    /**
     * prompts/soru-uret.txt ve prompts/soru-duzenle.txt isteğe bağlıdır
     * (bkz. quiz.ai.QuestionGenerator). QuestionGenerator kurucusu bu
     * dosyalara / klasöre hiç dokunmaz -- yönerge metni yalnızca generate()
     * ve revise() çağrıldığında, o an okunur ve dosya yoksa sessizce koda
     * gömülü metne düşülür. Bu yüzden "prompts" klasörü hiç var olmasa
     * (silinse, taşınsa) bile kurucu asla patlamamalı.
     */
    private static void testQuestionGeneratorSurvivesMissingPromptsDir() {
        boolean constructed;
        try {
            new QuestionGenerator();
            constructed = true;
        } catch (RuntimeException e) {
            constructed = false;
        }
        check("QuestionGenerator, prompts klasörü olmasa bile kurulabiliyor", constructed);
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

    /** Zorluk ayrıştırma testleri için geçici bir soru dosyası yazar. */
    private static Path writeTempQuestionsFile(String content) throws IOException {
        Path file = Files.createTempFile("selftest-questions-", ".txt");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
