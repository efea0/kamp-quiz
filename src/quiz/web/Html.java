package quiz.web;

/**
 * HTML uretimi icin kucuk yardimci.
 * Sablon motoru kullanmiyoruz; bagimlilik eklememek icin metin birlestiriyoruz.
 */
final class Html {

    private Html() {
    }

    static String page(String title, String body) {
        return page(title, body, "");
    }

    static String page(String title, String body, String headExtra) {
        return page(title, body, headExtra, "");
    }

    static String page(String title, String body, String headExtra, String bodyClass) {
        String safeClass = bodyClass == null ? "" : escape(bodyClass);
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
                <body class="%s">
                %s</body>
                </html>
                """.formatted(escape(title), headExtra, safeClass, body);
    }

    /** Oyuncu ekranlarında tarayıcı geri tuşunun eski soru görüntüsünü açmasını engeller. */
    static String playerNavigationGuard() {
        return """
                <script>
                  (function () {
                    history.scrollRestoration = 'manual';
                    history.replaceState({kampQuiz: true}, document.title, location.href);
                    history.pushState({kampQuiz: true}, document.title, location.href);
                    window.addEventListener('popstate', function () {
                      history.go(1);
                    });
                    window.addEventListener('pageshow', function (event) {
                      if (event.persisted) {
                        window.location.reload();
                      }
                    });
                  })();
                </script>
                """;
    }

    /** Tam sayfa otomatik yenilenen ekranlarda kullanıcının kaydırma konumunu korur. */
    static String projectionNavigationGuard() {
        return """
                <script>
                  (function () {
                    var scrollKey = 'kampQuizProjectionScroll:' + location.search;

                    function saveScroll() {
                      try {
                        sessionStorage.setItem(scrollKey, String(window.scrollY || window.pageYOffset || 0));
                      } catch (ignore) {
                        // Özel tarama modlarında sessionStorage kapalı olabilir.
                      }
                    }

                    function restoreScroll() {
                      try {
                        var raw = sessionStorage.getItem(scrollKey);
                        if (raw === null) return;
                        var y = Number(raw);
                        if (!Number.isFinite(y)) return;
                        requestAnimationFrame(function () {
                          window.scrollTo(0, y);
                          requestAnimationFrame(function () { window.scrollTo(0, y); });
                        });
                      } catch (ignore) {
                        // Kaydırma korunamazsa ekranın normal açılması engellenmez.
                      }
                    }

                    history.scrollRestoration = 'manual';
                    window.addEventListener('pagehide', saveScroll);
                    window.addEventListener('beforeunload', saveScroll);
                    window.addEventListener('pageshow', restoreScroll);
                    document.addEventListener('DOMContentLoaded', restoreScroll);
                  })();
                </script>
                """;
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

            body.theme-kagit {
              --bg: #f7f1e3; --surface: #fffaf0; --surface-2: #eadfca;
              --line: #c9b99a; --line-soft: #dfd0b7; --text: #2d241b; --muted: #715e4c;
              --green: #328a32; --green-dark: #236b23; --green-soft: rgba(50, 138, 50, .14);
              --red: #c53d3d; --red-dark: #9f2e2e; --red-soft: rgba(197, 61, 61, .13);
              --blue: #1769aa; --blue-dark: #0e4e83; --blue-soft: rgba(23, 105, 170, .13);
              --gold: #a76b00;
            }

            body.theme-neon {
              --bg: #100a2a; --surface: #20134a; --surface-2: #2c1e5d;
              --line: #5e3daa; --line-soft: #3b2877; --text: #fff6ff; --muted: #c8b9ee;
              --green: #5cffb1; --green-dark: #20cf7b; --green-soft: rgba(92, 255, 177, .14);
              --red: #ff6b9d; --red-dark: #d64478; --red-soft: rgba(255, 107, 157, .14);
              --blue: #65c7ff; --blue-dark: #38a4df; --blue-soft: rgba(101, 199, 255, .15);
              --gold: #ffe66d;
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

            .preference-grid { display: grid; gap: 14px; margin: 16px 0 20px; }
            .preference-group { border: 0; padding: 0; margin: 0; }
            .preference-group legend {
              font-size: .75rem; font-weight: 800; letter-spacing: 1.2px;
              text-transform: uppercase; color: var(--muted); margin-bottom: 8px;
            }
            .preference-options { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
            .pick { position: relative; cursor: pointer; }
            .pick input { position: absolute; opacity: 0; }
            .pick-content {
              display: flex; flex-direction: column; align-items: center; gap: 5px;
              min-height: 52px; padding: 9px 6px; text-align: center;
              background: var(--bg); border: 2px solid var(--line); border-radius: 12px;
              font-size: .78rem; font-weight: 700;
            }
            .pick input:checked + .pick-content { border-color: var(--blue); background: var(--blue-soft); }
            .pick input:focus-visible + .pick-content { outline: 2px solid var(--blue); outline-offset: 2px; }

            /* ---------- oda kurulum secim kartlari ---------- */

            .setup-card { display: grid; gap: 18px; min-width: 0; }
            .setup-group { border: 0; padding: 0; margin: 0; min-width: 0; }
            .setup-group + .setup-group { padding-top: 18px; border-top: 2px solid var(--line-soft); }
            .setup-group legend {
              display: block; width: 100%; padding: 0; margin: 0 0 10px;
              font-size: .75rem; font-weight: 800; letter-spacing: 1.2px;
              text-transform: uppercase; color: var(--muted);
            }
            .setup-options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; min-width: 0; }
            .setup-pick { display: block; position: relative; cursor: pointer; min-width: 0; }
            .setup-pick input { position: absolute; width: 1px; height: 1px; opacity: 0; }
            .setup-pick-content {
              display: grid; grid-template-columns: 32px minmax(0, 1fr) 22px; align-items: center; gap: 10px;
              min-height: 88px; padding: 12px;
              background: var(--bg); border: 2px solid var(--line);
              border-bottom-width: var(--shadow); border-radius: 14px;
              transition: background .15s, border-color .15s, transform .08s;
            }
            .setup-pick:hover .setup-pick-content { border-color: var(--blue); background: var(--surface-2); }
            .setup-pick:active .setup-pick-content { transform: translateY(2px); border-bottom-width: 2px; }
            .setup-pick input:checked + .setup-pick-content {
              background: var(--blue-soft); border-color: var(--blue);
              box-shadow: inset 0 0 0 1px var(--blue);
            }
            .setup-pick input:focus-visible + .setup-pick-content { outline: 2px solid var(--blue); outline-offset: 3px; }
            .setup-pick-icon {
              display: grid; place-items: center; width: 32px; height: 32px;
              border-radius: 10px; background: var(--surface-2); color: var(--muted);
              font-size: 1.15rem; font-weight: 900; line-height: 1;
            }
            .setup-pick input:checked + .setup-pick-content .setup-pick-icon { background: var(--blue); color: #04212e; }
            .setup-pick-copy { min-width: 0; }
            .setup-pick-copy b { display: block; font-size: .95rem; font-weight: 800; }
            .setup-pick-copy small { display: block; margin-top: 3px; color: var(--muted); font-size: .76rem; line-height: 1.25; }
            .setup-pick-mark {
              display: grid; place-items: center; width: 21px; height: 21px;
              border: 2px solid var(--line); border-radius: 50%; color: transparent;
              font-size: .75rem; font-weight: 900;
            }
            .setup-pick input:checked + .setup-pick-content .setup-pick-mark {
              border-color: var(--blue); background: var(--blue); color: #04212e;
            }
            .setup-toggle { margin-top: 0; padding-top: 16px; border-top: 2px solid var(--line-soft); }
            @media (max-width: 520px) {
              .setup-options { grid-template-columns: 1fr; }
              .setup-pick-content { min-height: 76px; }
            }

            .toggle { display: flex; align-items: center; gap: 9px; margin-top: 14px; color: var(--text); font-size: .88rem; font-weight: 700; }
            .toggle input { width: 18px; height: 18px; accent-color: var(--blue); }
            .avatar-choice .pick-content { min-height: 88px; }
            .avatar-svg { width: 44px; height: 62px; display: block; flex: 0 0 auto; overflow: visible; }
            .avatar-svg.small { width: 34px; height: 48px; }
            .avatar-choice .avatar-svg { width: 54px; height: 76px; }

            .server-clock {
              display: inline-flex; align-items: baseline; gap: 7px; margin: 10px auto 0;
              padding: 8px 13px; border: 2px solid var(--blue); border-radius: 999px;
              color: var(--blue); font-weight: 800; font-variant-numeric: tabular-nums;
            }
            .server-clock b { font-size: 1.35rem; }

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

            .reaction {
              display: flex; align-items: center; justify-content: center; gap: 12px;
              margin: 14px 0 20px; padding: 12px 14px; border: 2px solid var(--line);
              border-radius: var(--radius); background: var(--surface); text-align: left;
              animation: reaction-enter .35s ease-out both;
            }
            .reaction .avatar-svg { width: 98px; height: 138px; overflow: visible; flex: 0 0 auto; }
            .reaction b { display: block; font-size: .95rem; }
            .avatar-character,
            .avatar-character .character-head,
            .avatar-character .character-body,
            .avatar-character .character-arm,
            .avatar-character .character-leg,
            .avatar-character .character-tail,
            .avatar-character .character-prop,
            .avatar-character .character-face,
            .avatar-character .face-eye,
            .avatar-character .face-smile,
            .avatar-character .face-worried,
            .avatar-character .face-tear {
              transform-box: fill-box; transform-origin: center;
            }
            .avatar-character .face-worried,
            .avatar-character .face-tear { opacity: 0; }

            .reaction-correct-1 .avatar-svg .avatar-character { animation: avatar-jump 1.1s cubic-bezier(.2,.8,.25,1) both; }
            .reaction-correct-1 .avatar-svg .character-arm-left { animation: avatar-arm-left 1.05s ease-in-out both; }
            .reaction-correct-1 .avatar-svg .character-arm-right { animation: avatar-arm-right 1.05s ease-in-out both; }
            .reaction-correct-2 .avatar-svg .avatar-character { animation: avatar-spin 1.15s ease-out both; }
            .reaction-correct-2 .avatar-svg .face-eye { animation: avatar-blink .8s ease-in-out both; }
            .reaction-correct-3 .avatar-svg .avatar-character { animation: avatar-bounce 1s ease-in-out both; }
            .reaction-correct-3 .avatar-svg .character-arm { animation: avatar-arm-up 1s ease-in-out both; }
            .reaction-correct-4 .avatar-svg .avatar-character { animation: avatar-celebrate 1.1s ease-out both; }
            .reaction-correct-4 .avatar-svg .character-prop { animation: avatar-prop 1.1s ease-in-out both; }
            .reaction-correct-5 .avatar-svg .avatar-character { animation: avatar-twirl 1.2s ease-in-out both; }
            .reaction-correct-5 .avatar-svg .character-leg { animation: avatar-leg-tap 1.1s ease-in-out both; }
            .reaction-correct-1 .avatar-svg .face-smile,
            .reaction-correct-2 .avatar-svg .face-smile,
            .reaction-correct-3 .avatar-svg .face-smile,
            .reaction-correct-4 .avatar-svg .face-smile,
            .reaction-correct-5 .avatar-svg .face-smile { animation: avatar-smile .7s ease-out both; }

            .reaction-wrong-1 .avatar-svg .avatar-character { animation: avatar-shrink 1s ease-out both; }
            .reaction-wrong-2 .avatar-svg .avatar-character { animation: avatar-head-drop 1s ease-in-out both; }
            .reaction-wrong-2 .avatar-svg .character-head { animation: avatar-head-shake .9s ease-in-out both; }
            .reaction-wrong-3 .avatar-svg .avatar-character { animation: avatar-wobble 1s ease-in-out both; }
            .reaction-wrong-3 .avatar-svg .character-arm { animation: avatar-arm-drop .9s ease-in both; }
            .reaction-wrong-4 .avatar-svg .avatar-character { animation: avatar-sway 1.05s ease-in-out both; }
            .reaction-wrong-5 .avatar-svg .avatar-character { animation: avatar-flop 1.05s ease-in-out both; }
            .reaction-wrong-1 .avatar-svg .face-smile,
            .reaction-wrong-2 .avatar-svg .face-smile,
            .reaction-wrong-3 .avatar-svg .face-smile,
            .reaction-wrong-4 .avatar-svg .face-smile,
            .reaction-wrong-5 .avatar-svg .face-smile,
            .reaction-timeout-1 .avatar-svg .face-smile,
            .reaction-timeout-2 .avatar-svg .face-smile,
            .reaction-timeout-3 .avatar-svg .face-smile { opacity: 0; }
            .reaction-wrong-1 .avatar-svg .face-worried,
            .reaction-wrong-2 .avatar-svg .face-worried,
            .reaction-wrong-3 .avatar-svg .face-worried,
            .reaction-wrong-4 .avatar-svg .face-worried,
            .reaction-wrong-5 .avatar-svg .face-worried,
            .reaction-timeout-1 .avatar-svg .face-worried,
            .reaction-timeout-2 .avatar-svg .face-worried,
            .reaction-timeout-3 .avatar-svg .face-worried { opacity: 1; }
            .reaction-wrong-1 .avatar-svg .face-tear,
            .reaction-wrong-2 .avatar-svg .face-tear,
            .reaction-wrong-3 .avatar-svg .face-tear,
            .reaction-wrong-4 .avatar-svg .face-tear,
            .reaction-wrong-5 .avatar-svg .face-tear { opacity: 1; animation: avatar-tear .9s ease-in-out both; }

            .reaction-timeout-1 .avatar-svg .avatar-character { animation: avatar-timeout-sink 1s ease-in both; }
            .reaction-timeout-1 .avatar-svg .character-arm { animation: avatar-arm-drop .9s ease-in both; }
            .reaction-timeout-2 .avatar-svg .avatar-character { animation: avatar-timeout-drift 1.1s ease-in-out both; }
            .reaction-timeout-2 .avatar-svg .character-head { animation: avatar-head-drop 1s ease-in both; }
            .reaction-timeout-3 .avatar-svg .avatar-character { animation: avatar-timeout-sleep 1.1s ease-in-out both; }
            .reaction-timeout-3 .avatar-svg .face-tear { opacity: 1; animation: avatar-tear .9s ease-in-out both; }

            /* ---------- mac sonu sahnesi: karakter odakli kutlama ---------- */

            .reaction {
              position: relative; display: grid;
              grid-template-columns: minmax(145px, .9fr) minmax(0, 1.1fr);
              align-items: center; gap: 8px; min-height: 220px; overflow: hidden;
              padding: 0 16px 0 0; border-width: 2px;
              background: linear-gradient(118deg, var(--reaction-dark, var(--surface-2)), var(--surface) 66%);
            }
            .reaction::before {
              content: ""; position: absolute; inset: 0 42% 0 0;
              background: conic-gradient(from 225deg at 50% 76%, transparent, rgba(255,255,255,.16), transparent 32%);
              pointer-events: none;
            }
            .reaction-correct { --reaction-dark: #164d35; border-color: var(--green); }
            .reaction-wrong { --reaction-dark: #4a2630; border-color: var(--red); }
            .reaction-timeout { --reaction-dark: #40324f; border-color: var(--gold); }
            .reaction-stage {
              position: relative; display: grid; place-items: end center;
              min-height: 220px; align-self: stretch; isolation: isolate;
            }
            .reaction-stage::before {
              content: ""; position: absolute; inset: 10px 8px 18px;
              z-index: -2; border-radius: 50%;
              background: radial-gradient(ellipse at 50% 78%, rgba(255,255,255,.28), transparent 65%);
            }
            .reaction-stage::after {
              content: ""; position: absolute; left: 10%; right: 10%; bottom: 14px;
              z-index: -1; height: 22px; border-radius: 50%;
              background: rgba(0,0,0,.34); filter: blur(4px);
              transform: scaleX(.72); animation: stage-shadow-pulse 1.5s ease-out both;
            }
            .reaction-floor {
              position: absolute; left: 18%; right: 18%; bottom: 13px; height: 7px;
              border-radius: 50%; background: rgba(255,255,255,.25); filter: blur(1px);
            }
            .reaction .avatar-svg {
              position: relative; z-index: 2; width: 142px; height: 200px;
              overflow: visible; flex: 0 0 auto;
              filter: drop-shadow(0 8px 0 rgba(0,0,0,.2));
            }
            .reaction-copy { position: relative; z-index: 5; padding: 14px 0 14px 2px; }
            .reaction-copy b { display: block; font-size: 1rem; line-height: 1.35; }
            .reaction-badge {
              display: inline-block; margin-bottom: 10px; padding: 6px 10px;
              border-radius: 999px; color: #10251a; font-size: .68rem; font-weight: 900;
              letter-spacing: 1px; text-transform: uppercase;
              animation: badge-slam .46s cubic-bezier(.18, .9, .25, 1.25) both;
            }
            .reaction-correct .reaction-badge { background: var(--green); }
            .reaction-wrong .reaction-badge { background: var(--red); color: #fff; }
            .reaction-timeout .reaction-badge { background: var(--gold); }
            .reaction-confetti { position: absolute; inset: 0; z-index: 4; pointer-events: none; }
            .reaction-confetti i {
              position: absolute; left: 38%; top: 55%; width: 8px; height: 14px;
              border-radius: 2px; background: var(--green); opacity: 0;
              transform-origin: center; animation-delay: var(--delay, 0s);
            }
            .reaction-confetti i:nth-child(1) { --x: -90px; --y: -92px; --r: -48deg; --delay: .05s; background: var(--gold); }
            .reaction-confetti i:nth-child(2) { --x: -58px; --y: -122px; --r: 32deg; --delay: .1s; background: var(--blue); }
            .reaction-confetti i:nth-child(3) { --x: -18px; --y: -145px; --r: -18deg; --delay: .02s; background: var(--red); }
            .reaction-confetti i:nth-child(4) { --x: 28px; --y: -132px; --r: 58deg; --delay: .14s; background: var(--gold); }
            .reaction-confetti i:nth-child(5) { --x: 70px; --y: -106px; --r: -35deg; --delay: .08s; background: var(--blue); }
            .reaction-confetti i:nth-child(6) { --x: 100px; --y: -66px; --r: 44deg; --delay: .18s; background: var(--green); }
            .reaction-confetti i:nth-child(7) { --x: -104px; --y: -42px; --r: 22deg; --delay: .2s; background: var(--red); }
            .reaction-confetti i:nth-child(8) { --x: 112px; --y: -28px; --r: -52deg; --delay: .12s; background: var(--gold); }
            .reaction-correct .reaction-confetti i { animation: confetti-burst 1.05s cubic-bezier(.18,.72,.22,1) both; }
            .reaction-wrong .reaction-confetti i { width: 7px; height: 7px; border-radius: 50%; background: var(--red); animation: defeat-shards .72s ease-out both; }
            .reaction-timeout .reaction-confetti i { width: 7px; height: 7px; background: var(--muted); animation: timeout-dust .9s ease-out both; }

            .reaction-stage .avatar-character { transform-box: fill-box; transform-origin: center bottom; }
            .reaction-stage .character-arm-left { transform-box: fill-box; transform-origin: 100% 0%; stroke: rgba(16,37,46,.28); stroke-width: 1.4; stroke-linejoin: round; }
            .reaction-stage .character-arm-right { transform-box: fill-box; transform-origin: 0% 0%; stroke: rgba(16,37,46,.28); stroke-width: 1.4; stroke-linejoin: round; }
            .reaction-stage .character-leg { transform-box: fill-box; transform-origin: center 0%; }
            .reaction-correct-1 .avatar-svg .avatar-character { animation: hero-win-pop 1.55s cubic-bezier(.18,.82,.24,1.12) both; }
            .reaction-correct-1 .avatar-svg .character-arm-left { animation: hero-arm-left-up .9s ease-out .12s both; }
            .reaction-correct-1 .avatar-svg .character-arm-right { animation: hero-arm-right-up .9s ease-out .12s both; }
            .reaction-correct-2 .avatar-svg .avatar-character { animation: hero-win-spin 1.6s cubic-bezier(.2,.8,.2,1) both; }
            .reaction-correct-2 .avatar-svg .character-prop { animation: hero-prop-flash 1.2s ease-out .18s both; }
            .reaction-correct-3 .avatar-svg .avatar-character { animation: hero-win-dance 1.55s ease-in-out both; }
            .reaction-correct-3 .avatar-svg .character-arm { animation: hero-arm-wave .8s ease-in-out .15s 2 alternate both; }
            .reaction-correct-4 .avatar-svg .avatar-character { animation: hero-win-pose 1.5s cubic-bezier(.18,.9,.25,1.12) both; }
            .reaction-correct-4 .avatar-svg .character-head { animation: hero-head-nod .72s ease-in-out .3s both; }
            .reaction-correct-5 .avatar-svg .avatar-character { animation: hero-win-stomp 1.55s cubic-bezier(.18,.8,.25,1) both; }
            .reaction-correct-5 .avatar-svg .character-leg { animation: hero-leg-stomp .7s ease-in-out .18s 2 alternate both; }
            .reaction-correct .avatar-svg .face-smile { animation: hero-face-smile .55s ease-out .2s both; }

            .reaction-wrong-1 .avatar-svg .avatar-character { animation: hero-defeat-crumple 1.2s ease-in both; }
            .reaction-wrong-2 .avatar-svg .avatar-character { animation: hero-defeat-faceplant 1.25s ease-in-out both; }
            .reaction-wrong-3 .avatar-svg .avatar-character { animation: hero-defeat-wobble 1.2s ease-in-out both; }
            .reaction-wrong-4 .avatar-svg .avatar-character { animation: hero-defeat-shrug 1.2s ease-in-out both; }
            .reaction-wrong-5 .avatar-svg .avatar-character { animation: hero-defeat-flop 1.25s ease-in both; }
            .reaction-wrong .avatar-svg .character-head { animation: hero-head-sad .9s ease-in-out .2s both; }
            .reaction-wrong .avatar-svg .character-arm { animation: hero-arm-sad .85s ease-in .2s both; }

            .reaction-timeout-1 .avatar-svg .avatar-character { animation: hero-timeout-sink 1.25s ease-in both; }
            .reaction-timeout-2 .avatar-svg .avatar-character { animation: hero-timeout-drift 1.35s ease-in-out both; }
            .reaction-timeout-3 .avatar-svg .avatar-character { animation: hero-timeout-sleep 1.3s ease-in both; }
            .reaction-timeout .avatar-svg .character-head { animation: hero-head-sad .95s ease-in .15s both; }

            @keyframes stage-shadow-pulse { 0% { opacity: 0; transform: scaleX(.35); } 45% { opacity: .9; transform: scaleX(1); } 100% { opacity: .7; transform: scaleX(.72); } }
            @keyframes badge-slam { 0% { opacity: 0; transform: translateY(12px) rotate(-8deg) scale(.55); } 75% { transform: translateY(-3px) rotate(2deg) scale(1.08); } 100% { opacity: 1; transform: none; } }
            @keyframes confetti-burst { 0% { opacity: 0; transform: translate(0, 20px) rotate(0) scale(.3); } 18% { opacity: 1; } 100% { opacity: 0; transform: translate(var(--x), var(--y)) rotate(var(--r)) scale(1); } }
            @keyframes defeat-shards { 0% { opacity: 0; transform: translate(0, 0) scale(.2); } 20% { opacity: .8; } 100% { opacity: 0; transform: translate(var(--x), 12px) scale(1.3); } }
            @keyframes timeout-dust { 0% { opacity: 0; transform: translate(0, 0) scale(.2); } 25% { opacity: .65; } 100% { opacity: 0; transform: translate(var(--x), 20px) scale(1.1); } }
            @keyframes hero-win-pop { 0% { transform: translateY(34px) scale(.7,.55) rotate(-8deg); } 20% { transform: translateY(-8px) scale(1.13,.86) rotate(5deg); } 38% { transform: translateY(-25px) scale(.9,1.13) rotate(-3deg); } 58% { transform: translateY(2px) scale(1.08,.94) rotate(2deg); } 78% { transform: translateY(-8px) scale(.98,1.03); } 100% { transform: none; } }
            @keyframes hero-win-spin { 0% { transform: translateY(28px) rotate(-20deg) scale(.65); } 28% { transform: translateY(-18px) rotate(18deg) scale(1.08); } 52% { transform: translateY(0) rotate(-8deg) scale(1); } 70% { transform: translateY(-7px) rotate(5deg) scale(1.04); } 100% { transform: none; } }
            @keyframes hero-win-dance { 0%,100% { transform: translateY(0) rotate(0) scale(1); } 18% { transform: translateY(-16px) rotate(-7deg) scale(1.03,.96); } 35% { transform: translateY(1px) rotate(7deg) scale(.96,1.04); } 52% { transform: translateY(-12px) rotate(-5deg) scale(1.04,.97); } 72% { transform: translateY(0) rotate(4deg); } }
            @keyframes hero-win-pose { 0% { transform: translateY(24px) scale(.72,.65) rotate(-5deg); } 32% { transform: translateY(-14px) scale(1.1,.92) rotate(4deg); } 56% { transform: translateY(0) scale(.96,1.06) rotate(-2deg); } 72% { transform: translateY(-5px) scale(1.04,.98) rotate(2deg); } 100% { transform: none; } }
            @keyframes hero-win-stomp { 0% { transform: translateY(30px) scale(.75,.62); } 25% { transform: translateY(-20px) scale(1.06,.96); } 48% { transform: translateY(2px) scale(.94,1.08); } 66% { transform: translateY(-7px) scale(1.03,.98); } 100% { transform: none; } }
            @keyframes hero-arm-left-up { 0% { transform: rotate(0); } 45% { transform: rotate(-45deg) translateY(-11px); } 72% { transform: rotate(-25deg) translateY(-5px); } 100% { transform: rotate(-34deg) translateY(-8px); } }
            @keyframes hero-arm-right-up { 0% { transform: rotate(0); } 45% { transform: rotate(45deg) translateY(-11px); } 72% { transform: rotate(25deg) translateY(-5px); } 100% { transform: rotate(34deg) translateY(-8px); } }
            @keyframes hero-arm-wave { 0% { transform: rotate(-24deg); } 100% { transform: rotate(30deg) translateY(-8px); } }
            @keyframes hero-prop-flash { 0%,100% { transform: scale(1) rotate(0); filter: brightness(1); } 45% { transform: scale(1.35) rotate(24deg); filter: brightness(1.7); } }
            @keyframes hero-head-nod { 0%,100% { transform: rotate(0); } 45% { transform: rotate(-12deg) translateY(-3px); } 72% { transform: rotate(8deg); } }
            @keyframes hero-leg-stomp { 0%,100% { transform: rotate(0); } 55% { transform: rotate(-12deg) translateY(-5px); } }
            @keyframes hero-face-smile { 0% { opacity: .2; transform: scale(.4); } 65% { opacity: 1; transform: scale(1.35); } 100% { opacity: 1; transform: scale(1); } }
            @keyframes hero-defeat-crumple { 0% { transform: translateY(0) scale(1); } 42% { transform: translateY(10px) scale(.9,1.1) rotate(-5deg); } 100% { transform: translateY(20px) scale(1.05,.72) rotate(4deg); } }
            @keyframes hero-defeat-faceplant { 0% { transform: translateY(0) rotate(0); } 52% { transform: translateY(12px) rotate(18deg) scale(.95,.98); } 100% { transform: translateY(18px) rotate(28deg) scale(.9,.82); } }
            @keyframes hero-defeat-wobble { 0%,100% { transform: rotate(0); } 20% { transform: rotate(-12deg) translateX(-6px); } 42% { transform: rotate(11deg) translateX(7px); } 64% { transform: rotate(-6deg); } 82% { transform: rotate(4deg) translateY(8px); } }
            @keyframes hero-defeat-shrug { 0% { transform: translateY(0) scale(1); } 35% { transform: translateY(-5px) scale(.96,1.04); } 65% { transform: translateY(8px) rotate(-7deg); } 100% { transform: translateY(12px) rotate(5deg) scale(.95); } }
            @keyframes hero-defeat-flop { 0% { transform: translateY(0) rotate(0); } 45% { transform: translateY(8px) rotate(-18deg) scale(.95); } 75% { transform: translateY(18px) rotate(15deg) scale(.88,.92); } 100% { transform: translateY(16px) rotate(8deg) scale(.92,.88); } }
            @keyframes hero-head-sad { 0%,100% { transform: rotate(0); } 55% { transform: rotate(17deg) translateY(7px); } }
            @keyframes hero-arm-sad { 0% { transform: rotate(0); } 100% { transform: rotate(28deg) translateY(10px); } }
            @keyframes hero-timeout-sink { 0% { transform: translateY(0) scale(1); } 58% { transform: translateY(18px) scale(.96,.88) rotate(5deg); } 100% { transform: translateY(24px) scale(.9,.8) rotate(8deg); } }
            @keyframes hero-timeout-drift { 0%,100% { transform: translateX(0) rotate(0); } 36% { transform: translateX(-12px) rotate(-7deg); } 68% { transform: translateX(10px) rotate(6deg); } }
            @keyframes hero-timeout-sleep { 0% { transform: rotate(0); } 52% { transform: rotate(12deg) translateY(9px); } 100% { transform: rotate(8deg) translateY(15px) scale(.95); } }

            @media (max-width: 520px) {
              .reaction { grid-template-columns: 126px minmax(0, 1fr); min-height: 190px; padding-right: 10px; }
              .reaction-stage { min-height: 190px; }
              .reaction .avatar-svg { width: 116px; height: 164px; }
              .reaction-copy b { font-size: .88rem; }
              .reaction-badge { font-size: .6rem; }
            }

            .host-reaction {
              display: flex; align-items: center; gap: 12px; padding: 10px 14px;
              margin-bottom: 12px; border: 2px solid var(--gold); border-radius: 14px;
              background: var(--surface);
            }
            .host-reaction .avatar-svg { width: 76px; height: 106px; overflow: visible; }
            .host-reaction .avatar-svg .avatar-character { animation: avatar-celebrate 1.1s ease-out both; }
            .host-reaction strong { display: block; color: var(--gold); }
            .host-reaction small { color: var(--muted); }

            @keyframes reaction-enter { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
            @keyframes avatar-jump { 0%,100% { transform: translateY(0) rotate(0); } 30% { transform: translateY(-18px) rotate(-4deg); } 55% { transform: translateY(2px) rotate(3deg); } 75% { transform: translateY(-7px) rotate(-2deg); } }
            @keyframes avatar-spin { 0% { transform: rotate(0) scale(.85); } 45% { transform: rotate(-14deg) scale(1.05); } 80% { transform: rotate(9deg) scale(1); } }
            @keyframes avatar-bounce { 0%,100% { transform: translateY(0); } 25% { transform: translateY(-10px); } 45% { transform: translateY(0); } 65% { transform: translateY(-7px); } }
            @keyframes avatar-celebrate { 0% { transform: scale(.85) rotate(-5deg); } 45% { transform: scale(1.08) rotate(5deg); } 75% { transform: scale(1) rotate(-3deg); } }
            @keyframes avatar-twirl { 0% { transform: rotate(0) translateY(0); } 45% { transform: rotate(12deg) translateY(-8px); } 75% { transform: rotate(-8deg) translateY(1px); } }
            @keyframes avatar-arm-left { 0%,100% { transform: rotate(0); } 35% { transform: rotate(-28deg) translateY(-7px); } 70% { transform: rotate(8deg); } }
            @keyframes avatar-arm-right { 0%,100% { transform: rotate(0); } 35% { transform: rotate(28deg) translateY(-7px); } 70% { transform: rotate(-8deg); } }
            @keyframes avatar-arm-up { 0%,100% { transform: rotate(0); } 45% { transform: rotate(-22deg) translateY(-9px); } 70% { transform: rotate(20deg) translateY(-5px); } }
            @keyframes avatar-blink { 0%,70%,100% { transform: scaleY(1); } 78% { transform: scaleY(.12); } 86% { transform: scaleY(1); } }
            @keyframes avatar-prop { 0%,100% { transform: rotate(0); } 50% { transform: rotate(22deg) scale(1.2); } }
            @keyframes avatar-leg-tap { 0%,100% { transform: rotate(0); } 45% { transform: rotate(-10deg) translateY(-3px); } 70% { transform: rotate(8deg); } }
            @keyframes avatar-smile { 0% { transform: scale(.6); opacity: .3; } 65% { transform: scale(1.25); opacity: 1; } 100% { transform: scale(1); opacity: 1; } }
            @keyframes avatar-shrink { 0% { transform: scale(1); } 45% { transform: scale(.88) translateY(6px); } 100% { transform: scale(.94) translateY(3px); } }
            @keyframes avatar-head-drop { 0% { transform: rotate(0); } 65% { transform: rotate(13deg) translateY(5px); } 100% { transform: rotate(8deg) translateY(3px); } }
            @keyframes avatar-head-shake { 0%,100% { transform: rotate(0); } 30% { transform: rotate(-10deg); } 70% { transform: rotate(8deg); } }
            @keyframes avatar-wobble { 0%,100% { transform: rotate(0); } 30% { transform: rotate(-8deg); } 60% { transform: rotate(7deg); } 82% { transform: rotate(-3deg); } }
            @keyframes avatar-sway { 0%,100% { transform: translateX(0) rotate(0); } 50% { transform: translateX(9px) rotate(7deg); } }
            @keyframes avatar-flop { 0% { transform: rotate(0); } 55% { transform: rotate(-17deg) translateY(5px); } 100% { transform: rotate(10deg) translateY(3px); } }
            @keyframes avatar-arm-drop { 0% { transform: rotate(0); } 100% { transform: rotate(24deg) translateY(6px); } }
            @keyframes avatar-timeout-sink { 0% { transform: translateY(0); } 65% { transform: translateY(11px) rotate(4deg); } 100% { transform: translateY(7px) rotate(2deg); } }
            @keyframes avatar-timeout-drift { 0%,100% { transform: translateX(0); } 45% { transform: translateX(-8px) rotate(-5deg); } 75% { transform: translateX(7px) rotate(4deg); } }
            @keyframes avatar-timeout-sleep { 0% { transform: rotate(0); } 55% { transform: rotate(8deg) translateY(6px); } 100% { transform: rotate(5deg) translateY(4px); } }
            @keyframes avatar-tear { 0%,100% { transform: translateY(0); opacity: .2; } 45% { transform: translateY(6px); opacity: 1; } 80% { transform: translateY(2px); opacity: .8; } }
            @media (prefers-reduced-motion: reduce) {
              .reaction, .reaction *, .host-reaction, .host-reaction * { animation: none !important; }
            }

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
            .avatar-cell { flex: 0 0 34px; display: inline-flex; align-items: center; justify-content: center; }
            .avatar-cell .avatar-svg { width: 34px; height: 48px; }
            .waiting-avatar { margin: 0 auto 10px; }
            .waiting-avatar .avatar-svg { width: 76px; height: 106px; }
            .result-avatar { margin: 4px auto 8px; }
            .result-avatar .avatar-svg { width: 92px; height: 128px; }

            /* ---------- oda kodu ve projeksiyon ---------- */

            .joinbox { margin: 22px 0 4px; }
            .join-room-code {
              display: flex; align-items: center; justify-content: space-between; gap: 14px;
              margin: 22px 0 4px; padding: 14px 16px;
              border: 2px solid var(--blue); border-radius: 14px;
              background: var(--blue-soft);
            }
            .join-room-code span {
              color: var(--muted); font-size: .72rem; font-weight: 800;
              letter-spacing: 1.2px;
            }
            .join-room-code b {
              color: var(--blue); font-size: 1.5rem; font-weight: 800;
              letter-spacing: 5px; font-variant-numeric: tabular-nums;
            }
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

            .qr {
              display: flex; flex-direction: column; align-items: center; gap: 8px;
              margin: 18px 0 4px;
            }
            .qr svg { border-radius: 10px; display: block; }
            .qr span {
              font-size: 0.75rem; color: var(--muted);
              font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
            }
            .qr.small { margin: 12px 0 0; }
            .qr.small span { font-size: 0.65rem; }

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

            /* ---------- yanlis raporu ---------- */

            .missbar { height: 8px; background: var(--surface-2); border-radius: 999px; overflow: hidden; }
            .missbar span { display: block; height: 100%; background: var(--red); border-radius: 999px; }
            .missmeta { margin: 10px 0 0; font-size: 0.8rem; color: var(--muted); }
            .missmeta b { color: var(--red); }

            .notice {
              border: 2px solid var(--gold); border-radius: var(--radius);
              background: rgba(255, 200, 0, 0.1); color: var(--text);
              padding: 14px 16px; margin-bottom: 18px; font-size: 0.875rem;
            }
            code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                   font-size: 0.85em; color: var(--blue); }

            /* bekleme ekranindaki nabiz */
            .pulse {
              width: 54px; height: 54px; margin: 30px auto 0;
              border-radius: 50%; border: 3px solid var(--line);
              border-top-color: var(--blue); animation: spin 1.1s linear infinite;
            }
            @keyframes spin { to { transform: rotate(360deg); } }
            @media (prefers-reduced-motion: reduce) { .pulse { animation: none; } }

            .center { text-align: center; }
            a { color: var(--blue); }
            a.plain { color: var(--muted); text-decoration: none; font-weight: 700; font-size: 0.9rem; }
            a.plain:hover { color: var(--text); }
            """;
}
