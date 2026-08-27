package quiz.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lider tablosu. Skorlari bir metin dosyasina yazar ve geri okur.
 * Program kapansa bile skorlar kaybolmaz.
 *
 * Dosya bicimi:  isim|dogru|toplam|tarih
 */
public class Scoreboard {

    /**
     * Tek bir skor kaydi.
     * 'record' = sadece veri tasiyan kisa sinif. Java bunun icin
     * constructor'i, getter'lari ve equals/toString'i kendisi yazar.
     */
    public record Entry(String name, int score, int total, String date) {

        public int percentage() {
            return total == 0 ? 0 : Math.round(score * 100f / total);
        }
    }

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final Path file;

    public Scoreboard(Path file) {
        this.file = file;
    }

    /** Yeni bir skoru dosyanin SONUNA ekler (eskileri silmez). */
    public void save(String playerName, int score, int total) throws IOException {
        String safeName = playerName.replace("|", "-").trim();
        String line = safeName + "|" + score + "|" + total + "|"
                + LocalDateTime.now().format(DATE_FORMAT) + System.lineSeparator();

        Files.writeString(file, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,   // dosya yoksa olustur
                StandardOpenOption.APPEND);  // varsa sonuna ekle
    }

    /** En yuksek skorlu ilk 'limit' kaydi verir. */
    public List<Entry> topScores(int limit) throws IOException {
        List<Entry> entries = readAll();

        entries.sort(Comparator
                .comparingInt(Entry::percentage).reversed()   // once yuzde, buyukten kucuge
                .thenComparing(Comparator.comparingInt(Entry::score).reversed()));

        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }

    private List<Entry> readAll() throws IOException {
        List<Entry> entries = new ArrayList<>();
        if (!Files.exists(file)) {
            return entries;   // henuz kimse oynamamis
        }

        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String[] parts = line.split("\\|");
            if (parts.length < 4) {
                continue;   // bozuk satir, atla
            }
            try {
                entries.add(new Entry(
                        parts[0],
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()),
                        parts[3]));
            } catch (NumberFormatException e) {
                // sayiya cevrilemeyen satiri sessizce atla
            }
        }
        return entries;
    }
}
