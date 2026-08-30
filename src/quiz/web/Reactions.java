package quiz.web;

/** Oyuncu ve projeksiyon için güvenli, önceden tanımlı tepkiler. */
final class Reactions {

    private static final String[] CORRECT = {
            "%s'den hızlısı zil sesi!",
            "%s soruyu daha soru işareti gelmeden bitirdi!",
            "Şıklar hizaya geçti; %s geldi!",
            "Cevap geldi, soru kendini açıklamaya fırsat bulamadı!",
            "%s'nin parmağı düşüncelerinden önce davrandı!"
    };

    private static final String[] WRONG = {
            "%s bu şıkkı seçti; şık da neye uğradığını şaşırdı.",
            "Cevap geldi ama doğru cevapla aynı sınıfta değildi.",
            "%s'nin cevabı sahneye çıktı, doğru cevap kuliste kaldı.",
            "Soru ile cevap kısa bir süre göz göze geldi; sonra ayrıldılar.",
            "Bu cevap özgüvenle geldi, doğruluk yolda kaldı."
    };

    private static final String[] TIMEOUT = {
            "Sayaç öyle hızlandı ki zil bile şaşırdı.",
            "Cevap yetişmedi; parmaklar toplantıdaydı.",
            "Süre bitti, cevap hâlâ giriş kapısında."
    };

    private static final String[] HOST_LINES = {
            "%s sınıfın Wi-Fi'sinden hızlı bağlandı!",
            "%s soruyu daha yüklenmeden yakaladı!",
            "%s cevap sunucusuna ilk bağlanan oldu!",
            "%s şıklara baktı, doğru olan kendini belli etti!",
            "%s beyninin turbo modunu açtı!",
            "%s bu soruya ping atmadan bağlandı!",
            "%s doğru cevabı cache'lemiş gibi geldi!",
            "%s şıkları sıraya dizip doğruyu öne aldı!",
            "%s soruya speedrun yaptı; kayıt alındı!",
            "%s cevap butonuna değil, doğrudan hedefe bastı!"
    };

    private Reactions() {
    }

    static String studentFeedback(GameSession session, boolean correct, boolean timedOut, int seed) {
        if (session == null) {
            return "";
        }
        String[] messages = timedOut ? TIMEOUT : correct ? CORRECT : WRONG;
        String family = timedOut ? "timeout" : correct ? "correct" : "wrong";
        int variant = Math.floorMod(seed, messages.length) + 1;
        String message = String.format(messages[variant - 1], Html.escape(session.getPlayerName()));
        String badge = timedOut ? "Süre doldu" : correct ? "Doğru cevap" : "Bu tur olmadı";
        String confetti = "<div class=\"reaction-confetti\" aria-hidden=\"true\">"
                + "<i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i>"
                + "</div>";
        return "      <div class=\"reaction reaction-" + family + " reaction-" + family + "-" + variant
                + "\" role=\"status\">"
                + confetti
                + "<div class=\"reaction-stage\"><div class=\"reaction-floor\"></div>"
                + session.getAvatar().svg()
                + "</div><div class=\"reaction-copy\"><span class=\"reaction-badge\">"
                + badge + "</span><b>" + message + "</b></div></div>\n";
    }

    static String hostLine(String playerName, int seed) {
        String line = HOST_LINES[Math.floorMod(seed, HOST_LINES.length)];
        return String.format(line, Html.escape(playerName));
    }
}
