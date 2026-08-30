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

public class Scoreboard {



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


    public void save(String playerName, int score, int total, int points) throws IOException {
        String safeName = playerName.replace("|", "-").trim();
        String line = safeName + "|" + score + "|" + total + "|"
                + LocalDateTime.now().format(DATE_FORMAT) + "|" + points
                + System.lineSeparator();

        Files.writeString(file, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }


    public List<Entry> topScores(int limit) throws IOException {
        List<Entry> entries = readAll();

        entries.sort(Comparator
                .comparingInt(Entry::points).reversed()
                .thenComparing(Comparator.comparingInt(Entry::percentage).reversed()));

        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }

    private List<Entry> readAll() throws IOException {
        List<Entry> entries = new ArrayList<>();
        if (!Files.exists(file)) {
            return entries;
        }

        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String[] parts = line.split("\\|");
            if (parts.length < 4) {
                continue;
            }
            try {

                int points = parts.length >= 5 ? Integer.parseInt(parts[4].trim()) : 0;
                entries.add(new Entry(
                        parts[0],
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()),
                        parts[3],
                        points));
            } catch (NumberFormatException e) {

            }
        }
        return entries;
    }
}
