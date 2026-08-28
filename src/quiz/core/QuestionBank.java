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
 *
 * - dogruNo INSANIN saydigi gibi 1'den baslar (1 = ilk sik)
 * - '#' ile baslayan satirlar yorumdur, atlanir
 * - Bos satirlar atlanir
 * - '# baslik: Genel Kultur' satiri kategoriye gorunen bir ad verir
 * - '>' ile baslayan satir, bir onceki sorunun aciklamasidir
 */
public class QuestionBank {

    private static final String TITLE_PREFIX = "baslik:";

    private QuestionBank() {
        // Bu sinifin nesnesi uretilmez; sadece hazir (static) metotlari kullanilir.
    }

    /**
     * Klasordeki TUM .txt dosyalarini okur.
     * Uyarilari kimsenin gormedigi surum; ekrana basmaz.
     */
    public static List<Question> loadFromDirectory(Path directory) throws IOException {
        return loadFromDirectory(directory, new ArrayList<>());
    }

    /**
     * Klasordeki TUM .txt dosyalarini okur ve bozuk satir uyarilarini
     * verilen listeye YAZAR, ekrana basmaz.
     *
     * Bu ayrim onemli: core paketi ekrani bilmez. Uyariyi kimin nasil
     * gosterecegine arayuz karar verir - terminalde satir olarak, web'de
     * sayfada. Boylece ayni kod iki arayuzde de calisir.
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

        // Once dosyanin basligini ara; yoksa dosya adindan uret.
        String category = findTitle(lines).orElseGet(() -> categoryOf(file));

        // Aciklama satiri ('>') sorudan SONRA geldigi icin soruyu hemen kurmuyoruz;
        // bir sonraki soruya (veya dosya sonuna) kadar bekletiyoruz.
        String pendingLine = null;
        int pendingLineNumber = 0;
        StringBuilder pendingExplanation = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;   // yorum veya bos satir -> atla
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

            // Yeni bir soru satiri geldi: bekleyeni tamamla
            addPending(questions, pendingLine, pendingExplanation, category, file,
                    pendingLineNumber, warnings);

            pendingLine = line;
            pendingLineNumber = i + 1;
            pendingExplanation.setLength(0);
        }

        // Dosya bitti; son bekleyeni de tamamla
        addPending(questions, pendingLine, pendingExplanation, category, file,
                pendingLineNumber, warnings);

        return questions;
    }

    /** Bekleyen soru satirini ayristirip listeye ekler. */
    private static void addPending(List<Question> questions, String line,
                                   StringBuilder explanation, String category,
                                   Path file, int lineNumber, List<String> warnings) {
        if (line == null) {
            return;
        }
        try {
            questions.add(parseLine(line, category, explanation.toString()));
        } catch (IllegalArgumentException e) {
            // Tek bozuk satir yuzunden tum quiz cokmesin.
            warnings.add(file.getFileName() + " -> " + lineNumber
                    + ". satir atlandi: " + e.getMessage());
        }
    }

    /** Bir metin satirini Question nesnesine cevirir. */
    private static Question parseLine(String line, String category, String explanation) {
        String[] parts = line.split("\\|");

        if (parts.length < 4) {
            throw new IllegalArgumentException(
                    "En az 'soru | sik1 | sik2 | dogruNo' bicimi gerekli.");
        }

        String text = parts[0].trim();

        // Ilk parca soru, son parca dogru cevap numarasi; aradakiler siklar.
        int optionCount = parts.length - 2;
        String[] options = new String[optionCount];
        for (int i = 0; i < optionCount; i++) {
            options[i] = parts[i + 1].trim();
        }

        String lastPart = parts[parts.length - 1].trim();
        int humanNumber;
        try {
            humanNumber = Integer.parseInt(lastPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Son sutun bir sayi olmali, gelen deger: '" + lastPart + "'");
        }

        // Insan 1'den sayar, dizi 0'dan. Cevirme burada yapilir.
        return new Question(text, options, humanNumber - 1, category, explanation);
    }

    /**
     * Sorularda gecen kategorileri, tekrarsiz ve ilk gorulme sirasiyla verir.
     * Hem konsol hem web arayuzu bunu kullanir.
     */
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

    /** Dosyada '# baslik: ...' satiri varsa onun degerini bulur. */
    private static Optional<String> findTitle(List<String> lines) {
        for (String raw : lines) {
            String line = raw.trim();
            if (!line.startsWith("#")) {
                continue;
            }
            String withoutHash = line.substring(1).trim();
            if (withoutHash.toLowerCase().startsWith(TITLE_PREFIX)) {
                String title = withoutHash.substring(TITLE_PREFIX.length()).trim();
                if (!title.isEmpty()) {
                    return Optional.of(title);
                }
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
