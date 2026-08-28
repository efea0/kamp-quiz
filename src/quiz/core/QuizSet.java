package quiz.core;

import quiz.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hazir (onceden tanimli) bir test grubu: sabit bir isim, kisa bir aciklama,
 * soru basina sure siniri ve "hangi kategoriden kac soru" eslemesi tasir.
 *
 * Bu sinif de bir "model" sinifidir: veriyi tasir, ekrani hic bilmez.
 * Gercek soru secimi build() metodunda yapilir; sonucu sadece bir Question
 * listesidir, boylece Quiz sinifi normal (rastgele) modda oldugu gibi
 * bu listeyle de calisir.
 */
public class QuizSet {

    private final String name;                          // setin gorunen adi
    private final String description;                   // kisa tanitim, bos olabilir
    private final int timeLimitSeconds;                  // soru basina saniye
    private final Map<String, Integer> categoryCounts;   // kategori adi -> istenen soru sayisi
    private final Question.Difficulty difficultyFilter;  // istege bagli zorluk suzgeci, yoksa null

    /** Zorluk suzgeci olmadan set olusturur; eski cagrilarin bozulmamasi icin korunuyor. */
    public QuizSet(String name, String description, int timeLimitSeconds,
                   Map<String, Integer> categoryCounts) {
        this(name, description, timeLimitSeconds, categoryCounts, null);
    }

    public QuizSet(String name, String description, int timeLimitSeconds,
                   Map<String, Integer> categoryCounts, Question.Difficulty difficultyFilter) {
        // --- BEKCI KONTROLLERI ---
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
        // LinkedHashMap: kategori sirasi dosyadaki sirayla ayni kalsin
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

    /** Kategori -> istenen soru sayisi eslemesinin bir KOPYASINI verir. */
    public Map<String, Integer> getCategoryCounts() {
        return new LinkedHashMap<>(categoryCounts);
    }

    /** Setin zorluk suzgeci; yoksa null (tum zorluklar kabul edilir). */
    public Question.Difficulty getDifficultyFilter() {
        return difficultyFilter;
    }

    public boolean hasDifficultyFilter() {
        return difficultyFilter != null;
    }

    /** Setin istedigi TOPLAM soru sayisi (kategorilerdeki sayilarin toplami). */
    public int totalQuestions() {
        int total = 0;
        for (int count : categoryCounts.values()) {
            total += count;
        }
        return total;
    }

    /**
     * Verilen tum soru havuzundan, bu setin istedigi kategori ve sayilara gore
     * RASTGELE soru secer.
     *
     * Bir kategoride istenenden az soru varsa, hata firlatmak yerine var olan
     * kadarini alir (sessizce eksik doner) - test paketleri sorulardan bagimsiz
     * hazirlandigi icin bu durumu quiz'i cokertmeden idare etmek gerekir.
     *
     * Zorluk suzgeci varsa (bkz. difficultyFilter), her kategoride once o
     * zorluktaki sorular secilir; yeterli sayida yoksa eksik, hata
     * firlatilmadan, ayni kategorinin diger zorluklarindaki sorularla
     * tamamlanir.
     *
     * Donen liste karistirilmis haldedir; kategoriler blok blok gelmez.
     */
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

            // Once tam istenen zorluktan sec.
            List<Question> matching = new ArrayList<>(
                    QuestionBank.byDifficulty(pool, difficultyFilter));
            Collections.shuffle(matching);
            int take = Math.min(wanted, matching.size());
            List<Question> chosen = new ArrayList<>(matching.subList(0, take));

            // Eksik kaldiysa, ayni kategorideki diger zorluklardan tamamla.
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
