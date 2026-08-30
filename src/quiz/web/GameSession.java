package quiz.web;

import quiz.core.Quiz;
import quiz.model.Question;

/**
 * Tek bir oyuncunun web oturumu.
 * Sunucu ayni anda birden fazla oyuncuya hizmet verdigi icin,
 * her oyuncunun kendi Quiz nesnesi ve kendi ilerlemesi olmalidir.
 */
class GameSession {

    enum Theme {
        GECE("gece", "Gece"),
        KAGIT("kagit", "Kâğıt"),
        NEON("neon", "Neon");

        private final String id;
        private final String label;

        Theme(String id, String label) {
            this.id = id;
            this.label = label;
        }

        String id() {
            return id;
        }

        String label() {
            return label;
        }

        String cssClass() {
            return "theme-" + id;
        }

        static Theme from(String raw) {
            if (raw != null) {
                for (Theme theme : values()) {
                    if (theme.id.equalsIgnoreCase(raw.trim())) {
                        return theme;
                    }
                }
            }
            return GECE;
        }
    }

    enum Avatar {
        TILKI("tilki", "Tilki"),
        BALINA("balina", "Balina"),
        ROKET("roket", "Roket"),
        MANTAR("mantar", "Mantar"),
        ROBOT("robot", "Robot"),
        YAPRAK("yaprak", "Yaprak");

        private final String id;
        private final String label;

        Avatar(String id, String label) {
            this.id = id;
            this.label = label;
        }

        String id() {
            return id;
        }

        String label() {
            return label;
        }

        static Avatar from(String raw) {
            if (raw != null) {
                for (Avatar avatar : values()) {
                    if (avatar.id.equalsIgnoreCase(raw.trim())) {
                        return avatar;
                    }
                }
            }
            return ROKET;
        }

        /** Dış asset gerektirmeyen, özgün ve parçaları animasyonlanabilen karakter çizimi. */
        String svg() {
            String start = "<svg class=\"avatar-svg\" viewBox=\"0 0 100 140\" role=\"img\" aria-label=\""
                    + Html.escape(label) + "\" xmlns=\"http://www.w3.org/2000/svg\">";
            String art = switch (this) {
                case TILKI -> """
                        <path class="character-tail" d="M75 79q22-8 18 12-5 13-20 5" fill="#d97706"/>
                        <path class="character-leg character-leg-left" d="M38 101v25q0 6-8 6h-8q-3-5 4-8l3-23" fill="#b45309"/>
                        <path class="character-leg character-leg-right" d="M62 101v25q0 6 8 6h8q3-5-4-8l-3-23" fill="#b45309"/>
                        <path class="character-body" d="M33 61q17-9 34 0l8 43q-25 10-50 0Z" fill="#f59e0b"/>
                        <path class="character-arm character-arm-left" d="M34 68q-17 5-17 22l8 3 12-13" fill="#f59e0b"/>
                        <path class="character-arm character-arm-right" d="M66 68q17 5 17 22l-8 3-12-13" fill="#f59e0b"/>
                        <path class="character-head" d="M27 40 20 10l23 16q7-3 14 0L80 10l-7 30q-2 24-23 24T27 40Z" fill="#f59e0b"/>
                        """ + face();
                case BALINA -> """
                        <path class="character-tail" d="M77 89q18-10 19 2-5 9-17 5m9-7q8-8 10-1" fill="none" stroke="#0284c7" stroke-width="8" stroke-linecap="round"/>
                        <path class="character-leg character-leg-left" d="M39 103q-4 18-14 25 10 5 20-2l5-20" fill="#0369a1"/>
                        <path class="character-leg character-leg-right" d="M61 103q4 18 14 25-10 5-20-2l-5-20" fill="#0369a1"/>
                        <path class="character-body" d="M31 65q19-10 38 0l8 39q-27 12-54 0Z" fill="#38bdf8"/>
                        <path class="character-arm character-arm-left" d="M32 72q-17 8-16 22 8 5 17-4l10-11" fill="#38bdf8"/>
                        <path class="character-arm character-arm-right" d="M68 72q17 8 16 22-8 5-17-4L57 79" fill="#38bdf8"/>
                        <path class="character-head" d="M25 40q0-25 25-25t25 25q-2 24-25 24T25 40Z" fill="#7dd3fc"/>
                        <path class="character-prop" d="M45 16q-7-12 4-14 11 2 4 14" fill="none" stroke="#0284c7" stroke-width="5" stroke-linecap="round"/>
                        """ + face();
                case ROKET -> """
                        <path class="character-prop" d="M25 75q-12 9-7 25l10-5m47-20q12 9 7 25l-10-5" fill="#64748b"/>
                        <path class="character-leg character-leg-left" d="M39 103v24l-9 8h20l3-32" fill="#334155"/>
                        <path class="character-leg character-leg-right" d="M61 103v24l9 8H50l-3-32" fill="#334155"/>
                        <path class="character-body" d="M34 62q16-8 32 0l7 43q-23 9-46 0Z" fill="#fb7185"/>
                        <path class="character-arm character-arm-left" d="M34 70q-15 5-15 18l10 4 12-12" fill="#fb7185"/>
                        <path class="character-arm character-arm-right" d="M66 70q15 5 15 18l-10 4-12-12" fill="#fb7185"/>
                        <path class="character-head" d="M26 39q0-23 24-27 24 4 24 27-2 22-24 23T26 39Z" fill="#fde68a" stroke="#fb7185" stroke-width="8"/>
                        <path class="character-prop" d="M42 106q8 18 16 0" fill="none" stroke="#f97316" stroke-width="7" stroke-linecap="round"/>
                        """ + face();
                case MANTAR -> """
                        <path class="character-leg character-leg-left" d="M41 105v23l-9 7h18v-30" fill="#a855f7"/>
                        <path class="character-leg character-leg-right" d="M59 105v23l9 7H50v-30" fill="#a855f7"/>
                        <path class="character-body" d="M35 67h30l7 39q-22 10-44 0Z" fill="#f5d0fe"/>
                        <path class="character-arm character-arm-left" d="M34 72q-15 7-14 20l9 2 12-12" fill="#e9d5ff"/>
                        <path class="character-arm character-arm-right" d="M66 72q15 7 14 20l-9 2-12-12" fill="#e9d5ff"/>
                        <path class="character-head" d="M17 45q1-28 33-29t33 29Z" fill="#c084fc"/>
                        <circle class="character-prop" cx="29" cy="31" r="6" fill="#f5d0fe"/>
                        <circle class="character-prop" cx="67" cy="28" r="5" fill="#f5d0fe"/>
                        """ + face();
                case ROBOT -> """
                        <path class="character-leg character-leg-left" d="M37 106v25h16v-25" fill="#64748b"/>
                        <path class="character-leg character-leg-right" d="M63 106v25H47v-25" fill="#64748b"/>
                        <rect class="character-body" x="30" y="61" width="40" height="48" rx="12" fill="#94a3b8"/>
                        <path class="character-arm character-arm-left" d="M31 71q-15 3-16 17l9 6 13-12" fill="#94a3b8"/>
                        <path class="character-arm character-arm-right" d="M69 71q15 3 16 17l-9 6-13-12" fill="#94a3b8"/>
                        <rect class="character-head" x="22" y="18" width="56" height="45" rx="14" fill="#cbd5e1"/>
                        <path class="character-prop" d="M50 18V8" stroke="#94a3b8" stroke-width="5" stroke-linecap="round"/>
                        <circle class="character-prop" cx="50" cy="6" r="5" fill="#34d399"/>
                        """ + face();
                case YAPRAK -> """
                        <path class="character-leg character-leg-left" d="M43 104q-2 18-12 26h18l4-26" fill="#059669"/>
                        <path class="character-leg character-leg-right" d="M57 104q2 18 12 26H51l-4-26" fill="#059669"/>
                        <path class="character-body" d="M50 52q22 8 22 45-10 18-22 18T28 97q0-37 22-45Z" fill="#34d399"/>
                        <path class="character-arm character-arm-left" d="M34 70q-16 6-18 18 8 6 17-2l11-10" fill="#34d399"/>
                        <path class="character-arm character-arm-right" d="M66 70q16 6 18 18-8 6-17-2L56 76" fill="#34d399"/>
                        <path class="character-head" d="M50 15q26 1 28 25-6 25-28 31Q28 65 22 40q2-24 28-25Z" fill="#6ee7b7"/>
                        <path class="character-prop" d="M50 112q-18-29-25-53m25 53q18-29 25-53" fill="none" stroke="#047857" stroke-width="3" stroke-linecap="round"/>
                        """ + face();
            };
            return start + "<g class=\"avatar-character\">" + art + "</g></svg>";
        }

        /** Tüm karakterlerde aynı, animasyonla ifade değiştiren yüz parçaları. */
        private static String face() {
            return """
                    <g class="character-face">
                      <circle class="face-eye face-eye-left" cx="41" cy="40" r="3.5" fill="#172033"/>
                      <circle class="face-eye face-eye-right" cx="59" cy="40" r="3.5" fill="#172033"/>
                      <path class="face-smile" d="M39 49q11 10 22 0" fill="none" stroke="#172033" stroke-width="3" stroke-linecap="round"/>
                      <path class="face-worried" d="M39 54q11-9 22 0" fill="none" stroke="#172033" stroke-width="3" stroke-linecap="round"/>
                      <path class="face-tear face-tear-left" d="M39 45q-5 10 0 14" fill="none" stroke="#38bdf8" stroke-width="3" stroke-linecap="round"/>
                      <path class="face-tear face-tear-right" d="M61 45q5 10 0 14" fill="none" stroke="#38bdf8" stroke-width="3" stroke-linecap="round"/>
                    </g>
                    """;
        }
    }

    // Cevap ekraninda soruyu ve siklari tekrar gosterebilmek icin soru da tutulur.
    record Feedback(boolean correct, boolean timedOut, int earnedPoints,
                    Question question, int chosenIndex) {
    }

    private final String playerName;
    private final Quiz quiz;
    private final String roomCode;
    private final Theme theme;
    private final Avatar avatar;

    private Feedback feedback;
    private boolean scoreSaved;

    GameSession(String playerName, Quiz quiz, String roomCode) {
        this(playerName, quiz, roomCode, Theme.GECE, Avatar.ROKET);
    }

    GameSession(String playerName, Quiz quiz, String roomCode, Theme theme, Avatar avatar) {
        this.playerName = playerName;
        this.quiz = quiz;
        this.roomCode = roomCode;
        this.theme = theme == null ? Theme.GECE : theme;
        this.avatar = avatar == null ? Avatar.ROKET : avatar;
    }

    String getPlayerName() {
        return playerName;
    }

    String getRoomCode() {
        return roomCode;
    }

    Quiz getQuiz() {
        return quiz;
    }

    Theme getTheme() {
        return theme;
    }

    Avatar getAvatar() {
        return avatar;
    }

    Feedback getFeedback() {
        return feedback;
    }

    void setFeedback(Feedback feedback) {
        this.feedback = feedback;
    }

    void clearFeedback() {
        this.feedback = null;
    }

    boolean isScoreSaved() {
        return scoreSaved;
    }

    void markScoreSaved() {
        scoreSaved = true;
    }
}
