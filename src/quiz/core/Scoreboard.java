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
 * Dosya bicimi:  isim|dogru|toplam|tarih|puan
 *
 * Puan sonradan eklendi. Eski 4 sutunlu kayitlar hala okunur (puan = 0);
 * bu yuzden yeni alan basa degil SONA eklendi.
 */
public class Scoreboard {

    /**
     * Tek bir skor kaydi.
     * 'record' = sadece veri tasiyan kisa sinif. Java bunun icin
     * constructor'i, getter'lari ve equals/toString'i kendisi yazar.
     */
    public record Entry(String name, int score, int total, String date, int points) {

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
    public void save(String playerName, int score, int total, int points) throws IOException {
        String safeName = playerName.replace("|", "-").trim();
        String line = safeName + "|" + score + "|" + total + "|"
                + LocalDateTime.now().format(DATE_FORMAT) + "|" + points
                + System.lineSeparator();

        Files.writeString(file, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,   // dosya yoksa olustur
                StandardOpenOption.APPEND);  // varsa sonuna ekle
    }

    /** En yuksek skorlu ilk 'limit' kaydi verir. */
    public List<Entry> topScores(int limit) throws IOException {
        List<Entry> entries = readAll();

        entries.sort(Comparator
                .comparingInt(Entry::points).reversed()        // once puan, buyukten kucuge
                .thenComparing(Comparator.comparingInt(Entry::percentage).reversed()));

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
                // 5. sutun (puan) eski kayitlarda yok; o zaman 0 kabul edilir.
                int points = parts.length >= 5 ? Integer.parseInt(parts[4].trim()) : 0;
                entries.add(new Entry(
                        parts[0],
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()),
                        parts[3],
                        points));
            } catch (NumberFormatException e) {
                // sayiya cevrilemeyen satiri sessizce atla
            }
        }
        return entries;
    }
}
