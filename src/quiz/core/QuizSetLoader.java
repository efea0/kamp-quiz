package quiz.core;

import quiz.model.Question;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * sets/ klasorundeki .txt dosyalarini okuyup QuizSet nesnelerine cevirir.
 *
 * Dosya bicimi (bkz. sets/*.txt ornekleri):
 *     # baslik: Sabah Testi
 *     # aciklama: Gunu acmak icin kisa karisik tur      (istege bagli)
 *     # sure: 15                                        (istege bagli, yoksa 20)
 *     # zorluk: kolay                                   (istege bagli, yoksa tum zorluklar)
 *     Kategori Adi = sayi
 *     Baska Kategori = sayi
 *
 * - '#' ile baslayan diger satirlar yorumdur, atlanir
 * - Bos satirlar atlanir
 * - Kategori adlari questions/*.txt dosyalarindaki '# baslik:' degerleriyle
 *   BIREBIR eslesmeli; eslesmezse QuizSet.build() o kategoriden 0 soru getirir
 * - '# zorluk:' satiri 'kolay'/'orta'/'zor' degerlerinden birini alir;
 *   build() o zorluktaki sorulari oncelikli secer (bkz. QuizSet.build())
 *
 * Bu ozellik ISTEGE BAGLIDIR: sets/ klasoru yoksa ya da bossa, program
 * hata vermeden bos liste doner ve normal (rastgele N soru) mod calismaya
 * devam eder.
 */
public class QuizSetLoader {

    private static final String TITLE_PREFIX = "baslik:";
    private static final String DESCRIPTION_PREFIX = "aciklama:";
    private static final String TIME_PREFIX = "sure:";
    private static final String DIFFICULTY_PREFIX = "zorluk:";
    private static final int DEFAULT_TIME_LIMIT_SECONDS = 20;

    private QuizSetLoader() {
    }

    public static List<QuizSet> loadFromDirectory(Path directory) {
        return loadFromDirectory(directory, new ArrayList<>());
    }

    // Core paketi ekrani bilmez; uyarinin nasil gosterilecegine arayuz karar verir.
    public static List<QuizSet> loadFromDirectory(Path directory, List<String> warnings) {
        List<QuizSet> sets = new ArrayList<>();

        if (!Files.isDirectory(directory)) {
            // Ozellik istege bagli: klasor yoksa sessizce bos liste don.
            return sets;
        }

        try (Stream<Path> files = Files.list(directory)) {
            List<Path> txtFiles = files
                    .filter(p -> p.toString().toLowerCase().endsWith(".txt"))
                    .sorted()
                    .toList();

            for (Path file : txtFiles) {
                loadFromFile(file, warnings).ifPresent(sets::add);
            }
        } catch (IOException e) {
            warnings.add(directory + " klasoru okunamadi: " + e.getMessage());
        }

        return sets;
    }

    private static Optional<QuizSet> loadFromFile(Path file, List<String> warnings) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warnings.add(file.getFileName() + " okunamadi: " + e.getMessage());
            return Optional.empty();
        }

        String title = null;
        String description = "";
        int timeLimit = DEFAULT_TIME_LIMIT_SECONDS;
        Question.Difficulty difficultyFilter = null;
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            int lineNumber = i + 1;

            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("#")) {
                String withoutHash = line.substring(1).trim();
                String lower = withoutHash.toLowerCase();

                if (lower.startsWith(TITLE_PREFIX)) {
                    title = withoutHash.substring(TITLE_PREFIX.length()).trim();
                } else if (lower.startsWith(DESCRIPTION_PREFIX)) {
                    description = withoutHash.substring(DESCRIPTION_PREFIX.length()).trim();
                } else if (lower.startsWith(TIME_PREFIX)) {
                    String raw = withoutHash.substring(TIME_PREFIX.length()).trim();
                    try {
                        int parsed = Integer.parseInt(raw);
                        if (parsed <= 0) {
                            throw new NumberFormatException("sure pozitif olmali");
                        }
                        timeLimit = parsed;
                    } catch (NumberFormatException e) {
                        warnings.add(file.getFileName() + " -> " + lineNumber
                                + ". satir atlandi: sure bir pozitif sayi olmali: '" + raw + "'");
                    }
                } else if (lower.startsWith(DIFFICULTY_PREFIX)) {
                    String raw = withoutHash.substring(DIFFICULTY_PREFIX.length()).trim();
                    Optional<Question.Difficulty> parsed = Question.Difficulty.fromText(raw);
                    if (parsed.isPresent()) {
                        difficultyFilter = parsed.get();
                    } else {
                        warnings.add(file.getFileName() + " -> " + lineNumber
                                + ". satir atlandi: zorluk 'kolay', 'orta' veya 'zor' olmali: '"
                                + raw + "'");
                    }
                }
                continue;
            }

            int eq = line.indexOf('=');
            if (eq < 0) {
                warnings.add(file.getFileName() + " -> " + lineNumber
                        + ". satir atlandi: '=' isareti bulunamadi");
                continue;
            }

            String category = line.substring(0, eq).trim();
            String countRaw = line.substring(eq + 1).trim();

            if (category.isEmpty()) {
                warnings.add(file.getFileName() + " -> " + lineNumber
                        + ". satir atlandi: kategori adi bos");
                continue;
            }

            int count;
            try {
                count = Integer.parseInt(countRaw);
            } catch (NumberFormatException e) {
                warnings.add(file.getFileName() + " -> " + lineNumber
                        + ". satir atlandi: sayi degil: '" + countRaw + "'");
                continue;
            }

            if (count <= 0) {
                warnings.add(file.getFileName() + " -> " + lineNumber
                        + ". satir atlandi: sayi pozitif olmali: " + count);
                continue;
            }

            categoryCounts.put(category, count);
        }

        if (title == null || title.isBlank()) {
            warnings.add(file.getFileName()
                    + " -> '# baslik:' satiri yok, dosya atlandi");
            return Optional.empty();
        }

        if (categoryCounts.isEmpty()) {
            warnings.add(file.getFileName()
                    + " -> hic kategori satiri yok, dosya atlandi");
            return Optional.empty();
        }

        try {
            return Optional.of(
                    new QuizSet(title, description, timeLimit, categoryCounts, difficultyFilter));
        } catch (IllegalArgumentException e) {
            warnings.add(file.getFileName() + " -> set olusturulamadi: " + e.getMessage());
            return Optional.empty();
        }
    }
}
