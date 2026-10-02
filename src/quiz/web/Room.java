package quiz.web;

import quiz.core.Quiz;
import quiz.core.QuizSet;
import quiz.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bir oyun odasi: hoca acar, katilimcilar 4 haneli kodla girer.
 *
 * Soru sirasi iki turlu olabilir:
 *
 *   PAYLASIK (varsayilan)  Herkes ayni sorulari ayni sirada gorur.
 *                          Kahoot/Wayground boyle calisir; perdedeki siralama
 *                          anlamli olur cunku herkes ayni soruda yarisir.
 *
 *   KISIYE OZEL            Her oyuncuya ayri secim ve ayri sira verilir.
 *                          Yan yana oturanlar kopyalayamaz ama siralama
 *                          "kim hangi soruda" bilgisini kaybeder.
 */
class Room {

    enum Mode {
        SERBEST,
        SENKRON
    }

    enum Phase {
        LOBI,
        SORU,
        CEVAP,
        BITTI
    }

    private final String code;
    private final QuizSet set;
    private final Mode mode;
    private final boolean sharedOrder;

    // --- senkron akisin durumu ---
    private volatile Phase phase = Phase.LOBI;
    private volatile int index = 0;
    private volatile long questionStartedAt = 0;
    private final long createdAt = System.currentTimeMillis();

    /**
     * Odaya son bakildigi an (epoch ms). ServerContext, oda bos kaldigi sure
     * dolunca bu odayi silmek icin kullanir. volatile: temizlik baska bir
     * is parcacigindan okur.
     */
    private volatile long lastSeen = System.currentTimeMillis();

    /** Paylasik sirada herkesin aldigi tek liste; ilk oyuncuda uretilir. */
    private volatile List<Question> sharedQuestions;

    /** Odadaki isimler (kucuk harfe cevrilmis). Ayni isim iki kez giremez. */
    private final Set<String> takenNames = ConcurrentHashMap.newKeySet();

    /** Birden fazla istek ayni anda listeyi degistirebilmelidir. */
    private final List<GameSession> players = new CopyOnWriteArrayList<>();

    // --- projeksiyon ekranindaki canli tepki seridi icin ---

    /** /ekran her yenilendiginde bir artar; tepki seridini sirayla dondurmek icin. */
    private final AtomicInteger screenTick = new AtomicInteger();

    /** Bir onceki /ekran yenilemesindeki puan sirasi; "kim yukseldi" hesabi icin. */
    private volatile List<String> lastScreenOrder = List.of();

    Room(String code, QuizSet set, Mode mode, boolean sharedOrder) {
        this.code = code;
        this.set = set;
        this.mode = mode;
        // Senkron akista herkes ayni soruyu gormek ZORUNDA; secim yok.
        this.sharedOrder = mode == Mode.SENKRON || sharedOrder;
    }

    boolean isSharedOrder() {
        return sharedOrder;
    }

    Mode getMode() {
        return mode;
    }

    boolean isSynchronous() {
        return mode == Mode.SENKRON;
    }

    Phase getPhase() {
        return phase;
    }

    int getIndex() {
        return index;
    }

    /** Senkron odada su an sorulan soru. */
    Question currentQuestion(List<Question> allQuestions) {
        List<Question> questions = questionList(allQuestions);
        return index < questions.size() ? questions.get(index) : null;
    }

    int questionCount(List<Question> allQuestions) {
        return questionList(allQuestions).size();
    }

    /** Bu soruda kac saniye kaldi. */
    int remainingSeconds() {
        if (questionStartedAt == 0) {
            return set.getTimeLimitSeconds();
        }
        long left = set.getTimeLimitSeconds() * 1000L - (System.currentTimeMillis() - questionStartedAt);
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    /** Hoca "Başlat" dedi. */
    synchronized void start(List<Question> allQuestions) {
        if (phase != Phase.LOBI) {
            return;
        }
        questionList(allQuestions);
        index = 0;
        phase = Phase.SORU;
        questionStartedAt = System.currentTimeMillis();
    }

    /**
     * Test bittikten sonra AYNI odada ikinci tur baslatir.
     *
     * Neden var: Oyuncu bitince "Bir tur daha?" goruncesi cikiyor, ama hoca
     * tarafinda oda yalnizca "test bitti" yaziyordu. Hoca ikinci turu
     * baslatabilmek icin /kur'a donup YENI oda kurmak zorundaydi; o eski
     * oyuncular yeni odaya girmiyor, sinif yariya kadar bos kaliyordu.
     *
     * Davranis:
     *  - Her oyuncuya YENI bir Quiz verilir (skor sifirlanir)
     *  - Paylasik sirada ise sorular yeniden karistirilir; ayni sira tekrar
     *    etmesin diye. Kisiye-ozel sirada zaten her oyuncu kendi karisimini
     *    aliyor, dokunulmuyor.
     *  - Faz LOBI'ye doner: hoca "Baslat" deyince ilk soru gelir
     *
     * Onceki turun skoru scores.txt'e YAZILMIS oldugu icin kaybolmaz; sadece
     * odadaki anlik durum sifirlanir.
     */
    synchronized void startSecondRound(List<Question> allQuestions) {
        // Serbest odada index oyunu yonetmez; oyuncular kendi hizinda
        // ilerler ve faz BITTI olmaz. "Bitti" durumu everyoneFinished() ile
        // belirlendigi icin kontrolu displayPhase() ile yapmaliyiz, yoksa
        // serbest odada bu metot hicbir sey yapmaz.
        if (displayPhase() != Phase.BITTI) {
            return;
        }

        if (sharedOrder) {
            // Ayni soru sirasi iki kez gelmesin diye yeniden karistir.
            sharedQuestions = null;
        }

        for (GameSession player : players) {
            player.replaceQuiz(newQuiz(allQuestions));
            player.clearFeedback();
        }

        index = 0;
        phase = Phase.LOBI;
        lastScreenOrder = List.of();
        questionStartedAt = 0;
        screenTick.set(0);
    }

    /**
     * Hoca "Cevabı göster" dedi.
     * Cevap vermeyenler yanlis sayilir; yoksa siradan kopup kalirlar.
     */
    synchronized void reveal() {
        if (phase != Phase.SORU) {
            return;
        }
        for (GameSession player : players) {
            Quiz quiz = player.getQuiz();
            while (quiz.hasNext() && quiz.getQuestionNumber() - 1 <= index) {
                quiz.startQuestionTimer();
                quiz.submitAnswer(-1);            // cevapsiz = yanlis
                player.clearFeedback();
            }
        }
        phase = Phase.CEVAP;
    }

    /** Hoca "Sonraki soru" dedi. */
    synchronized void next(List<Question> allQuestions) {
        if (phase != Phase.CEVAP) {
            return;
        }
        index++;
        if (index >= questionList(allQuestions).size()) {
            phase = Phase.BITTI;
        } else {
            phase = Phase.SORU;
            questionStartedAt = System.currentTimeMillis();
        }
    }

    /** Paylasik soru listesini uretir ya da hazir olani verir. */
    private List<Question> questionList(List<Question> allQuestions) {
        if (sharedQuestions == null) {
            synchronized (this) {
                if (sharedQuestions == null) {
                    List<Question> built = set.build(allQuestions);
                    Collections.shuffle(built);
                    sharedQuestions = List.copyOf(built);
                }
            }
        }
        return sharedQuestions;
    }

    String getCode() {
        return code;
    }

    QuizSet getSet() {
        return set;
    }

    long getCreatedAt() {
        return createdAt;
    }

    /**
     * Odaya giren oyuncu icin quiz uretir.
     * Uretimin bir kez olmasi icin senkronize; iki kisi ayni anda katilirsa
     * ikisi de ayni sorulari gormeli.
     */
    Quiz newQuiz(List<Question> allQuestions) {
        List<Question> questions = sharedOrder ? questionList(allQuestions) : set.build(allQuestions);

        Quiz quiz = new Quiz(questions);
        if (!sharedOrder) {
            quiz.shuffle();
        }
        quiz.setTimeLimitSeconds(set.getTimeLimitSeconds());
        return quiz;
    }

    boolean isNameTaken(String name) {
        return takenNames.contains(normalize(name));
    }

    /** Ismi rezerve eder; zaten alinmissa false doner. */
    boolean reserveName(String name) {
        return takenNames.add(normalize(name));
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(java.util.Locale.forLanguageTag("tr"));
    }

    /** Odaya her bakildiginda (ekran, panel, katilim) cagrilir. */
    void touch() {
        lastSeen = System.currentTimeMillis();
    }

    long lastSeen() {
        return lastSeen;
    }

    void addPlayer(GameSession session) {
        players.add(session);
    }

    int playerCount() {
        return players.size();
    }

    /**
     * Artik var olmayan oturumlari oyuncu listesinden cikarir ve kalan
     * oyuncu sayisini doner.
     *
     * Neden gerekli: temizlikte oturum once silinir; odadaki liste ise o
     * oturumu hala tutuyor olur. "Oda bos mu?" sorusu bu yuzden "listedeki
     * herkes hala sessions haritasinda mi?" diye sorulmali. Aksi halde hicbir
     * oda hicbir zaman bos sayilmaz ve temizlik hicbir seyi silmez.
     */
    int activePlayerCount(java.util.Set<String> livingSessionIds) {
        players.removeIf(player -> !livingSessionIds.contains(player.sessionId()));
        return players.size();
    }

    /** Puana gore sirali katilimci listesi; projeksiyon ekrani bunu gosterir. */
    List<GameSession> standings() {
        List<GameSession> sorted = new ArrayList<>(players);
        sorted.sort(Comparator
                .comparingInt((GameSession s) -> s.getQuiz().getPoints()).reversed()
                .thenComparing(s -> s.getPlayerName()));
        return sorted;
    }

    /** Sira yukselten oyuncunun adi ve kac basamak yukseldigi. */
    record RankClimb(String name, int gain) {
    }

    /**
     * "Yukselen" tepkisi icin: bir onceki /ekran yenilemesine gore en cok
     * basamak cikan oyuncu. Cagrisi ayni zamanda "onceki sira" anini
     * gunceller — bu yuzden yalnizca projeksiyon ekraninin kendisi (bir
     * yenilemede bir kez) cagirmali, yoksa kiyaslama anlamsizlasir.
     */
    synchronized RankClimb climbSinceLastScreen(List<GameSession> standings) {
        List<String> currentOrder = new ArrayList<>();
        for (GameSession player : standings) {
            currentOrder.add(player.getPlayerName());
        }

        String climber = null;
        int bestGain = 0;
        for (int i = 0; i < currentOrder.size(); i++) {
            int oldPos = lastScreenOrder.indexOf(currentOrder.get(i));
            if (oldPos < 0) {
                continue;   // yeni katilimci, kiyaslanacak eski sirasi yok
            }
            int gain = oldPos - i;
            if (gain > bestGain) {
                bestGain = gain;
                climber = currentOrder.get(i);
            }
        }
        lastScreenOrder = currentOrder;

        // Tek basamaklik oynamalar surekli olur; gurultu olmasin diye en az 2 basamak arayalim.
        return bestGain >= 2 ? new RankClimb(climber, bestGain) : null;
    }

    /** /ekran yenilendikce artan sayac; tepki seridini sirayla dondurmek icin. */
    int nextScreenTick() {
        return screenTick.getAndIncrement();
    }

    /** Herkes testi bitirdi mi? */
    boolean everyoneFinished() {
        if (players.isEmpty()) {
            return false;
        }
        for (GameSession session : players) {
            if (session.getQuiz().hasNext()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Hoca panelinin gostereceği faz.
     *
     * SERBEST modda faz hiçbir zaman BITTI olmaz: herkes kendi hızında
     * ilerler, "tüm oyuncular bitti" anı oyuncuların Quiz'inde belirir,
     * odanın index'inde değil. Eski kod sadece index'e bakıyordu, bu yüzden
     * oyun bitince panel hâlâ "Cevabı göster" diyordu ve ikinci tur
     * başlatılamıyordu.
     *
     * SENKRON modda index oda temposunu yonetir, o yüzden oldugu gibi
     * durur.
     */
    Phase displayPhase() {
        if (!isSynchronous() && everyoneFinished()) {
            return Phase.BITTI;
        }
        return phase;
    }
}
