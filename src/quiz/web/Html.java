package quiz.web;

/**
 * HTML uretimi icin kucuk yardimci.
 * Sablon motoru kullanmiyoruz; bagimlilik eklememek icin metin birlestiriyoruz.
 */
final class Html {

    private Html() {
    }

    /** Her sayfanin ortak iskeleti. */
    static String page(String title, String body) {
        return page(title, body, "");
    }

    /**
     * head bolumune ek satir koyabilen surum.
     * Otomatik yenilenen ekranlar icin: meta http-equiv="refresh"
     */
    static String page(String title, String body, String headExtra) {
        return """
                <!DOCTYPE html>
                <html lang="tr">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta name="theme-color" content="#131f24">
                  <title>%s</title>
                  <link rel="stylesheet" href="/style.css">
                %s</head>
                <body>
                %s</body>
                </html>
                """.formatted(escape(title), headExtra, body);
    }

    /**
     * Metindeki HTML anlamli karakterleri zararsiz hale getirir.
     * Bu olmadan bir soru metnindeki '<' isareti sayfanin yapisini bozabilir.
     */
    static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /** Sik numarasini harfe cevirir: 0 -> A, 1 -> B ... */
    static String letter(int index) {
        return index < 26 ? String.valueOf((char) ('A' + index)) : String.valueOf(index + 1);
    }

    static final String CSS = """
            /* ------------------------------------------------------------------
               Koyu tema. Renkler rol adiyla tanimlanir; boylece tema tek yerden
               degisir ve sayfalarda ham renk kodu gecmez.
               ------------------------------------------------------------------ */
            :root {
              --bg:        #131f24;
              --surface:   #1b2b32;
              --surface-2: #223740;
              --line:      #37464f;
              --line-soft: #2b3d45;
              --text:      #f0f7fa;
              --muted:     #93a2ad;

              --green:      #58cc02;
              --green-dark: #43a303;
              --green-soft: rgba(88, 204, 2, 0.14);
              --red:        #ff5a5a;
              --red-dark:   #d03b3b;
              --red-soft:   rgba(255, 90, 90, 0.13);
              --blue:       #1cb0f6;
              --blue-dark:  #1591cc;
              --blue-soft:  rgba(28, 176, 246, 0.14);
              --gold:       #ffc800;

              --radius: 16px;
              --shadow: 4px;   /* butonlarin alt kalinligi */
            }

            * { box-sizing: border-box; }

            html { -webkit-text-size-adjust: 100%; }

            body {
              margin: 0;
              min-height: 100vh;
              background: var(--bg);
              color: var(--text);
              font-family: ui-rounded, "SF Pro Rounded", "Nunito", system-ui,
                           -apple-system, "Segoe UI", Roboto, Arial, sans-serif;
              font-size: 17px;
              line-height: 1.45;
              -webkit-font-smoothing: antialiased;
            }

            .screen {
              max-width: 620px;
              margin: 0 auto;
              padding: 20px 20px 40px;
              min-height: 100vh;
              display: flex;
              flex-direction: column;
            }

            /* Form da dikey flex olmali; yoksa icindeki .actions "margin-top:auto"
               ile asagi itilemez ve buton sayfanin ortasinda asili kalir. */
            .screen > form { display: flex; flex-direction: column; flex: 1; }

            /* ---------- ustbilgi: ilerleme + sayac ---------- */

            .topbar { display: flex; align-items: center; gap: 14px; margin-bottom: 26px; }

            /* Dikkat: .screen bir dikey flex konteyneri. Buraya flex:1 yazilirsa
               cubuk yatayda degil DIKEYDE buyur ve sayfayi kaplar. */
            .bar { flex: none; height: 14px; background: var(--surface-2); border-radius: 999px; overflow: hidden; }
            .topbar .bar { flex: 1; }
            .bar > i {
              display: block; height: 100%;
              background: var(--green);
              border-radius: 999px;
              transition: width .35s ease;
            }
            .bar.time { height: 6px; opacity: .85; }
            .bar.time > i { background: var(--blue); transition: width 1s linear; }
            .bar.time.hurry > i { background: var(--red); }

            .clock {
              font-weight: 800; font-size: 1.25rem; min-width: 2.2ch; text-align: right;
              font-variant-numeric: tabular-nums; color: var(--muted);
            }
            .clock.hurry { color: var(--red); }

            /* ---------- metin ---------- */

            h1 { font-size: 1.7rem; font-weight: 800; margin: 0 0 6px; letter-spacing: -0.4px; }
            h2 { font-size: 1.35rem; font-weight: 800; margin: 0 0 22px; line-height: 1.3; letter-spacing: -0.2px; }
            p  { margin: 0 0 14px; }
            .muted { color: var(--muted); }
            .small { font-size: 0.875rem; }

            .eyebrow {
              font-size: 0.75rem; font-weight: 800; letter-spacing: 1.4px;
              text-transform: uppercase; color: var(--muted); margin-bottom: 10px;
            }

            /* ---------- siklar ---------- */

            .choices { display: grid; gap: 12px; margin: 0 0 26px; }

            .choice { display: block; position: relative; cursor: pointer; }
            .choice input { position: absolute; opacity: 0; width: 0; height: 0; }

            .choice > span {
              display: flex; align-items: center; gap: 14px;
              padding: 15px 16px;
              background: var(--surface);
              border: 2px solid var(--line);
              border-bottom-width: var(--shadow);
              border-radius: var(--radius);
              font-weight: 600;
              transition: background .12s, border-color .12s, transform .06s;
            }
            .choice > span::before {
              content: attr(data-key);
              flex: 0 0 30px; height: 30px;
              display: grid; place-items: center;
              border: 2px solid var(--line);
              border-radius: 9px;
              font-size: 0.8rem; font-weight: 800; color: var(--muted);
            }
            .choice input:checked + span {
              background: var(--blue-soft);
              border-color: var(--blue);
            }
            .choice input:checked + span::before { border-color: var(--blue); color: var(--blue); }
            .choice input:focus-visible + span { outline: 2px solid var(--blue); outline-offset: 2px; }

            /* cevap sonrasi durumlar */
            .choice.is-right > span  { background: var(--green-soft); border-color: var(--green); }
            .choice.is-right > span::before { border-color: var(--green); color: var(--green); }
            .choice.is-wrong > span  { background: var(--red-soft); border-color: var(--red); }
            .choice.is-wrong > span::before { border-color: var(--red); color: var(--red); }
            .choice.is-dim > span { opacity: 0.5; }

            /* ---------- hazir test kartlari ---------- */

            .setlist { display: grid; gap: 12px; margin-top: 4px; }
            .setcard {
              display: block; width: 100%; text-align: left;
              font: inherit; color: var(--text);
              background: var(--surface);
              border: 2px solid var(--line); border-bottom-width: var(--shadow);
              border-radius: var(--radius);
              padding: 15px 17px; cursor: pointer;
              transition: border-color .12s, background .12s, transform .06s;
            }
            .setcard:hover { border-color: var(--blue); background: var(--surface-2); }
            .setcard:active { transform: translateY(2px); border-bottom-width: 2px; }
            .setcard b { display: block; font-size: 1.05rem; font-weight: 800; }
            .setcard small { display: block; color: var(--muted); font-size: 0.875rem; margin-top: 2px; }
            .setcard em {
              display: inline-block; margin-top: 8px; font-style: normal;
              font-size: 0.72rem; font-weight: 800; letter-spacing: 1px;
              text-transform: uppercase; color: var(--blue);
            }

            /* ---------- butonlar ---------- */

            .btn {
              display: block; width: 100%;
              padding: 15px 20px;
              font: inherit; font-weight: 800; font-size: 1rem;
              letter-spacing: 0.8px; text-transform: uppercase; text-align: center;
              text-decoration: none;
              color: #10251a;
              background: var(--green);
              border: 0; border-bottom: var(--shadow) solid var(--green-dark);
              border-radius: var(--radius);
              cursor: pointer;
              transition: filter .12s, transform .06s;
            }
            .btn:hover { filter: brightness(1.06); }
            .btn:active { transform: translateY(2px); border-bottom-width: 2px; }
            .btn.blue { background: var(--blue); border-bottom-color: var(--blue-dark); color: #04212e; }
            .btn.ghost {
              background: transparent; color: var(--muted);
              border: 2px solid var(--line); border-bottom-width: var(--shadow);
              text-transform: none; letter-spacing: 0;
            }
            .btn.ghost:hover { color: var(--text); }
            .btn[disabled] { opacity: .45; cursor: not-allowed; }

            .actions { margin-top: auto; padding-top: 20px; display: grid; gap: 12px; }

            /* ---------- cevap sonrasi geri bildirim ---------- */

            .verdict {
              border-radius: var(--radius);
              padding: 18px 20px;
              margin-bottom: 22px;
              border: 2px solid;
            }
            .verdict.ok   { background: var(--green-soft); border-color: var(--green); }
            .verdict.bad  { background: var(--red-soft);   border-color: var(--red); }
            .verdict h3 {
              margin: 0; font-size: 1.15rem; font-weight: 800;
              display: flex; align-items: center; justify-content: space-between; gap: 12px;
            }
            .verdict.ok  h3 { color: var(--green); }
            .verdict.bad h3 { color: var(--red); }
            .verdict .gain { font-size: 0.95rem; font-variant-numeric: tabular-nums; }
            .verdict .why { margin-top: 10px; font-size: 0.95rem; color: var(--text); opacity: .9; }

            /* ---------- kartlar, formlar ---------- */

            .card {
              background: var(--surface);
              border: 2px solid var(--line);
              border-bottom-width: var(--shadow);
              border-radius: var(--radius);
              padding: 22px;
              margin-bottom: 16px;
            }

            label.field {
              display: block; font-size: 0.75rem; font-weight: 800;
              letter-spacing: 1.2px; text-transform: uppercase;
              color: var(--muted); margin: 0 0 8px;
            }
            input[type=text], select {
              width: 100%; padding: 14px 15px; margin-bottom: 18px;
              font: inherit; font-weight: 600;
              background: var(--bg); color: var(--text);
              border: 2px solid var(--line); border-radius: 12px;
              appearance: none;
            }
            input[type=text]:focus, select:focus { outline: 0; border-color: var(--blue); }

            /* ---------- sonuc ---------- */

            .bigscore {
              font-size: 3.4rem; font-weight: 800; line-height: 1;
              color: var(--gold); font-variant-numeric: tabular-nums;
              margin: 6px 0 4px;
            }
            .stats { display: flex; gap: 12px; margin-bottom: 16px; }
            .stat {
              flex: 1; background: var(--surface); border: 2px solid var(--line);
              border-bottom-width: var(--shadow); border-radius: 14px;
              padding: 14px; text-align: center;
            }
            .stat b { display: block; font-size: 1.5rem; font-weight: 800; font-variant-numeric: tabular-nums; }
            .stat span { font-size: 0.7rem; font-weight: 800; letter-spacing: 1.1px;
                         text-transform: uppercase; color: var(--muted); }

            /* ---------- lider tablosu ---------- */

            .rank { display: grid; gap: 8px; }
            .rank .row {
              display: flex; align-items: center; gap: 14px;
              background: var(--surface); border: 2px solid var(--line);
              border-bottom-width: var(--shadow); border-radius: 14px;
              padding: 12px 16px;
            }
            .rank .row.me { border-color: var(--blue); }
            .rank .pos {
              flex: 0 0 28px; text-align: center; font-weight: 800; color: var(--muted);
              font-variant-numeric: tabular-nums;
            }
            .rank .row:nth-child(1) .pos { color: var(--gold); }
            .rank .who { flex: 1; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .rank .pts { font-weight: 800; color: var(--green); font-variant-numeric: tabular-nums; }
            .rank .sub { color: var(--muted); font-size: 0.8rem; font-variant-numeric: tabular-nums; }

            /* ---------- oda kodu ve projeksiyon ---------- */

            .joinbox { margin: 22px 0 4px; }
            .joinrow { display: flex; gap: 10px; align-items: stretch; }
            .joinrow input { margin-bottom: 0; }
            .joinrow .btn { width: auto; flex: 0 0 auto; padding-inline: 22px; }
            .codeinput {
              flex: 0 0 6.5ch; text-align: center;
              font-weight: 800; font-size: 1.2rem; letter-spacing: 3px;
              font-variant-numeric: tabular-nums;
            }

            .divider {
              display: flex; align-items: center; gap: 14px;
              color: var(--muted); font-size: 0.75rem; font-weight: 800;
              letter-spacing: 1.2px; text-transform: uppercase;
              margin: 26px 0 16px;
            }
            .divider::before, .divider::after {
              content: ""; flex: 1; height: 2px; background: var(--line-soft);
            }

            .code {
              font-size: 3.6rem; font-weight: 800; text-align: center;
              letter-spacing: 10px; color: var(--blue);
              font-variant-numeric: tabular-nums;
              margin: 14px 0 6px; padding-left: 10px;
            }

            .screenhead { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
            .codebox {
              text-align: right; border: 2px solid var(--line); border-bottom-width: var(--shadow);
              border-radius: 14px; padding: 10px 16px; background: var(--surface);
            }
            .codebox span {
              display: block; font-size: 0.65rem; font-weight: 800;
              letter-spacing: 1.2px; text-transform: uppercase; color: var(--muted);
            }
            .codebox b {
              font-size: 1.9rem; font-weight: 800; letter-spacing: 5px; color: var(--blue);
              font-variant-numeric: tabular-nums;
            }

            .screen.wide { max-width: 900px; }
            .rank.big .row { padding: 16px 20px; font-size: 1.15rem; }
            .rank.big .pos { flex: 0 0 40px; font-size: 1.3rem; }
            .rank.big .pts { font-size: 1.4rem; }

            /* ---------- taslak duzenleyici ---------- */

            .draft {
              width: 100%; padding: 14px; font-size: 0.82rem; line-height: 1.6;
              font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
              background: var(--bg); color: var(--text);
              border: 2px solid var(--line); border-radius: 12px;
              resize: vertical; white-space: pre; overflow-x: auto;
            }
            .draft:focus { outline: 0; border-color: var(--blue); }

            .code-block {
              background: var(--bg); border: 2px solid var(--line); border-radius: 10px;
              padding: 12px 14px; font-size: 0.8rem; overflow-x: auto;
              font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
              color: var(--text); margin: 0 0 14px;
            }

            .center { text-align: center; }
            a { color: var(--blue); }
            a.plain { color: var(--muted); text-decoration: none; font-weight: 700; font-size: 0.9rem; }
            a.plain:hover { color: var(--text); }
            """;
}
