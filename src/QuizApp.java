public class QuizApp {

    public static void main(String[] args) {
        System.out.println("=====================================");
        System.out.println("        KAMP QUIZ MOTORU v0.2        ");
        System.out.println("=====================================");
        System.out.println();

        // Question kalibindan gercek bir soru NESNESI uretiyoruz
        Question soru = new Question(
                "Java'da bir programin basladigi metodun adi nedir?",
                new String[] { "start()", "main()", "run()", "init()" },
                1   // dogru cevap: sirasi 1 olan sik -> main()
        );

        // Soruyu ekrana basalim
        System.out.println("SORU: " + soru.getText());

        String[] siklar = soru.getOptions();
        for (int i = 0; i < siklar.length; i++) {
            System.out.println("   " + (i + 1) + ") " + siklar[i]);
        }

        System.out.println();
        System.out.println("--- Kontrol testi ---");
        System.out.println("1 numarali sik dogru mu? " + soru.isCorrect(0));
        System.out.println("2 numarali sik dogru mu? " + soru.isCorrect(1));
    }
}
