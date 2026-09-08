package quiz.core;

import quiz.model.Question;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Soru bankasi: questions/ klasorundeki .txt dosyalarini okuyup
 * Question nesnelerine cevirir.
 *
 * Dosya formati (her satir bir soru):
 *     Soru metni | sik1 | sik2 | sik3 | sik4 | dogruNo
 *     Soru metni | sik1 | sik2 | sik3 | sik4 | dogruNo | zorluk   (istege bagli sutun)
 *
 * - dogruNo 1'den baslar (1 = ilk sik)
 * - Son sutun 'kolay'/'orta'/'zor' ise zorluk olarak okunur; o zaman dogru
 *   cevap SONDAN IKINCI sutundur. Bu sutun yoksa eski bicim aynen calisir.
 * - '# baslik: X' kategoriye gorunen ad verir; '# zorluk: X' dosya geneli
 *   varsayilan zorluk verir (satir sonundaki sutun her zaman kazanir).
 * - '>' ile baslayan satir, bir onceki sorunun aciklamasidir
 */
public class QuestionBank {

    private static final String TITLE_PREFIX = "baslik:";
    private static final String DIFFICULTY_PREFIX = "zorluk:";

    private QuestionBank() {
    }

    /** Klasordeki TUM .txt dosyalarini okur; uyarilari yok sayar. */
    public static List<Question> loadFromDirectory(Path directory) throws IOException {
        return loadFromDirectory(directory, new ArrayList<>());
    }

    /**
     * Klasordeki TUM .txt dosyalarini okur ve bozuk satir uyarilarini
     * verilen listeye YAZAR, ekrana basmaz.
     *
     * Bu ayrim onemli: core paketi ekrani bilmez. Uyariyi kimin nasil
     * gosterecegine arayuz karar verir; boylece ayni kod iki arayuzde de calisir.
     */
    public static List<Question> loadFromDirectory(Path directory, List<String> warnings)
            throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("Soru klasoru bulunamadi: " + directory.toAbsolutePath());
        }

        List<Question> all = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> txtFiles = files
                    .filter(p -> p.toString().toLowerCase().endsWith(".txt"))
                    .sorted()
                    .toList();

            for (Path file : txtFiles) {
                all.addAll(loadFromFile(file, warnings));
            }
        }
        return all;
    }

    /** Tek bir dosyayi okur; uyarilari yok sayar. */
    public static List<Question> loadFromFile(Path file) throws IOException {
        return loadFromFile(file, new ArrayList<>());
    }

    /** Tek bir dosyayi okur. Bozuk satirlari atlar, uyariyi listeye ekler. */
    public static List<Question> loadFromFile(Path file, List<String> warnings)
            throws IOException {
        List<Question> questions = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        String category = findTitle(lines).orElseGet(() -> categoryOf(file));
        Question.Difficulty fileDifficulty = findDifficulty(lines).orElse(null);

        // Aciklama satiri ('>') sorudan SONRA geldigi icin soruyu hemen kurmuyoruz;
        // bir sonraki soruya (veya dosya sonuna) kadar bekletiyoruz.
        String pendingLine = null;
        int pendingLineNumber = 0;
        StringBuilder pendingExplanation = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            if (line.startsWith(">")) {
                if (pendingLine != null) {
                    if (pendingExplanation.length() > 0) {
                        pendingExplanation.append(' ');
                    }
                    pendingExplanation.append(line.substring(1).trim());
                }
                continue;
            }

            addPending(questions, pendingLine, pendingExplanation, category, fileDifficulty,
                    file, pendingLineNumber, warnings);

            pendingLine = line;
            pendingLineNumber = i + 1;
            pendingExplanation.setLength(0);
        }

        addPending(questions, pendingLine, pendingExplanation, category, fileDifficulty,
                file, pendingLineNumber, warnings);

        return questions;
    }

    /** Bekleyen soru satirini ayristirip listeye ekler. */
    private static void addPending(List<Question> questions, String line,
                                   StringBuilder explanation, String category,
                                   Question.Difficulty fileDifficulty,
                                   Path file, int lineNumber, List<String> warnings) {
        if (line == null) {
            return;
        }
        try {
            questions.add(parseLine(line, category, explanation.toString(), fileDifficulty));
        } catch (IllegalArgumentException e) {
            // Tek bozuk satir yuzunden tum quiz cokmesin.
            warnings.add(file.getFileName() + " -> " + lineNumber
                    + ". satir atlandi: " + e.getMessage());
        }
    }

    /** Bir metin satirini Question nesnesine cevirir. */
    private static Question parseLine(String line, String category, String explanation,
                                      Question.Difficulty fileDifficulty) {
        String[] parts = line.split("\\|");

        if (parts.length < 4) {
            throw new IllegalArgumentException(
                    "En az 'soru | sik1 | sik2 | dogruNo' bicimi gerekli.");
        }

        String text = parts[0].trim();

        // Son sutun zorluk kelimesiyse dogru cevap numarasi SONDAN IKINCI sutuna kayar.
        String lastPart = parts[parts.length - 1].trim();
        Optional<Question.Difficulty> lineDifficulty = parts.length >= 5
                ? Question.Difficulty.fromText(lastPart)
                : Optional.empty();

        int correctColumn = lineDifficulty.isPresent() ? parts.length - 2 : parts.length - 1;
        int optionCount = correctColumn - 1;

        String[] options = new String[optionCount];
        for (int i = 0; i < optionCount; i++) {
            options[i] = parts[i + 1].trim();
        }

        String correctRaw = parts[correctColumn].trim();
        int humanNumber;
        try {
            humanNumber = Integer.parseInt(correctRaw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Son sutun bir sayi olmali, gelen deger: '" + correctRaw + "'");
        }

        // Insan 1'den sayar, dizi 0'dan.
        return new Question(text, options, humanNumber - 1, category, explanation,
                lineDifficulty.orElse(fileDifficulty));
    }

    /** Sorularda gecen kategorileri, tekrarsiz ve ilk gorulme sirasiyla verir. */
    public static List<String> categoriesOf(List<Question> questions) {
        Set<String> unique = new LinkedHashSet<>();
        for (Question q : questions) {
            unique.add(q.getCategory());
        }
        return new ArrayList<>(unique);
    }

    /** Sadece belirli bir kategorideki sorulari suzer. */
    public static List<Question> byCategory(List<Question> questions, String category) {
        List<Question> result = new ArrayList<>();
        for (Question q : questions) {
            if (q.getCategory().equals(category)) {
                result.add(q);
            }
        }
        return result;
    }

    /** Sadece belirli bir zorluktaki sorulari suzer. */
    public static List<Question> byDifficulty(List<Question> questions,
                                               Question.Difficulty difficulty) {
        List<Question> result = new ArrayList<>();
        for (Question q : questions) {
            if (q.getDifficulty() == difficulty) {
                result.add(q);
            }
        }
        return result;
    }

    /** Dosyada '# baslik: ...' satiri varsa onun degerini bulur. */
    private static Optional<String> findTitle(List<String> lines) {
        return findHeader(lines, TITLE_PREFIX).flatMap(value ->
                value.isEmpty() ? Optional.empty() : Optional.of(value));
    }

    /** Dosyada '# zorluk: ...' satiri varsa onun degerini bulur. */
    private static Optional<Question.Difficulty> findDifficulty(List<String> lines) {
        return findHeader(lines, DIFFICULTY_PREFIX).flatMap(Question.Difficulty::fromText);
    }

    /** '#' satirlari icinde verilen oneki tasan ilk satirin degerini dondurur. */
    private static Optional<String> findHeader(List<String> lines, String prefix) {
        for (String raw : lines) {
            String line = raw.trim();
            if (!line.startsWith("#")) {
                continue;
            }
            String withoutHash = line.substring(1).trim();
            if (withoutHash.toLowerCase().startsWith(prefix)) {
                return Optional.of(withoutHash.substring(prefix.length()).trim());
            }
        }
        return Optional.empty();
    }

    /** "genel-kultur.txt" -> "genel kultur" */
    private static String categoryOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.replace('-', ' ').replace('_', ' ');
    }
}
