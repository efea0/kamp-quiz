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

    public QuizSet(String name, String description, int timeLimitSeconds,
                   Map<String, Integer> categoryCounts) {
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
            Collections.shuffle(pool);

            int take = Math.min(wanted, pool.size());
            result.addAll(pool.subList(0, take));
        }

        Collections.shuffle(result);
        return result;
    }
}
