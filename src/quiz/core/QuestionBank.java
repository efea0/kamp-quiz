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

public class QuestionBank {

    private static final String TITLE_PREFIX = "baslik:";
    private static final String DIFFICULTY_PREFIX = "zorluk:";

    private QuestionBank() {

    }



    public static List<Question> loadFromDirectory(Path directory) throws IOException {
        return loadFromDirectory(directory, new ArrayList<>());
    }



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


    public static List<Question> loadFromFile(Path file) throws IOException {
        return loadFromFile(file, new ArrayList<>());
    }


    public static List<Question> loadFromFile(Path file, List<String> warnings)
            throws IOException {
        List<Question> questions = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);


        String category = findTitle(lines).orElseGet(() -> categoryOf(file));

        Question.Difficulty fileDifficulty = findDifficulty(lines).orElse(null);



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

            warnings.add(file.getFileName() + " -> " + lineNumber
                    + ". satir atlandi: " + e.getMessage());
        }
    }


    private static Question parseLine(String line, String category, String explanation,
                                      Question.Difficulty fileDifficulty) {
        String[] parts = line.split("\\|");

        if (parts.length < 4) {
            throw new IllegalArgumentException(
                    "En az 'soru | sik1 | sik2 | dogruNo' bicimi gerekli.");
        }

        String text = parts[0].trim();



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


        Question.Difficulty difficulty = lineDifficulty.orElse(fileDifficulty);


        return new Question(text, options, humanNumber - 1, category, explanation, difficulty);
    }



    public static List<String> categoriesOf(List<Question> questions) {
        Set<String> unique = new LinkedHashSet<>();
        for (Question q : questions) {
            unique.add(q.getCategory());
        }
        return new ArrayList<>(unique);
    }


    public static List<Question> byCategory(List<Question> questions, String category) {
        List<Question> result = new ArrayList<>();
        for (Question q : questions) {
            if (q.getCategory().equals(category)) {
                result.add(q);
            }
        }
        return result;
    }


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


    private static Optional<Question.Difficulty> findDifficulty(List<String> lines) {
        for (String raw : lines) {
            String line = raw.trim();
            if (!line.startsWith("#")) {
                continue;
            }
            String withoutHash = line.substring(1).trim();
            if (withoutHash.toLowerCase().startsWith(DIFFICULTY_PREFIX)) {
                String value = withoutHash.substring(DIFFICULTY_PREFIX.length()).trim();
                Optional<Question.Difficulty> parsed = Question.Difficulty.fromText(value);
                if (parsed.isPresent()) {
                    return parsed;
                }
            }
        }
        return Optional.empty();
    }


    private static String categoryOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.replace('-', ' ').replace('_', ' ');
    }
}
