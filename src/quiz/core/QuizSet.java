package quiz.core;

import quiz.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QuizSet {

    private final String name;
    private final String description;
    private final int timeLimitSeconds;
    private final Map<String, Integer> categoryCounts;
    private final Question.Difficulty difficultyFilter;


    public QuizSet(String name, String description, int timeLimitSeconds,
                   Map<String, Integer> categoryCounts) {
        this(name, description, timeLimitSeconds, categoryCounts, null);
    }

    public QuizSet(String name, String description, int timeLimitSeconds,
                   Map<String, Integer> categoryCounts, Question.Difficulty difficultyFilter) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Set adi bos olamaz.");
        }
        if (timeLimitSeconds <= 0) {
            throw new IllegalArgumentException("Sure siniri pozitif olmali: " + timeLimitSeconds);
        }
        if (categoryCounts == null || categoryCounts.isEmpty()) {
            throw new IllegalArgumentException("Set en az 1 kategori icermeli.");
        }
        for (Map.Entry<String, Integer> entry : categoryCounts.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("Kategori adi bos olamaz.");
            }
            if (entry.getValue() == null || entry.getValue() <= 0) {
                throw new IllegalArgumentException(
                        "'" + entry.getKey() + "' icin istenen soru sayisi pozitif olmali.");
            }
        }

        this.name = name;
        this.description = description == null ? "" : description.trim();
        this.timeLimitSeconds = timeLimitSeconds;

        this.categoryCounts = new LinkedHashMap<>(categoryCounts);
        this.difficultyFilter = difficultyFilter;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean hasDescription() {
        return !description.isEmpty();
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }


    public Map<String, Integer> getCategoryCounts() {
        return new LinkedHashMap<>(categoryCounts);
    }


    public Question.Difficulty getDifficultyFilter() {
        return difficultyFilter;
    }

    public boolean hasDifficultyFilter() {
        return difficultyFilter != null;
    }


    public int totalQuestions() {
        int total = 0;
        for (int count : categoryCounts.values()) {
            total += count;
        }
        return total;
    }



    public List<Question> build(List<Question> allQuestions) {
        if (allQuestions == null) {
            throw new IllegalArgumentException("Soru havuzu null olamaz.");
        }

        List<Question> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : categoryCounts.entrySet()) {
            String category = entry.getKey();
            int wanted = entry.getValue();

            List<Question> pool = new ArrayList<>(QuestionBank.byCategory(allQuestions, category));

            if (difficultyFilter == null) {
                Collections.shuffle(pool);
                result.addAll(pool.subList(0, Math.min(wanted, pool.size())));
                continue;
            }


            List<Question> matching = new ArrayList<>(
                    QuestionBank.byDifficulty(pool, difficultyFilter));
            Collections.shuffle(matching);
            int take = Math.min(wanted, matching.size());
            List<Question> chosen = new ArrayList<>(matching.subList(0, take));


            int missing = wanted - chosen.size();
            if (missing > 0) {
                List<Question> rest = new ArrayList<>(pool);
                rest.removeAll(matching);
                Collections.shuffle(rest);
                chosen.addAll(rest.subList(0, Math.min(missing, rest.size())));
            }

            result.addAll(chosen);
        }

        Collections.shuffle(result);
        return result;
    }
}
