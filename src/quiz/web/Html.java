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
        return """
                <!DOCTYPE html>
                <html lang="tr">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <link rel="stylesheet" href="/style.css">
                </head>
                <body>
                  <main class="wrap">
                %s
                  </main>
                </body>
                </html>
                """.formatted(escape(title), body);
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

    static final String CSS = """
            :root {
              --bg: #0d1117; --panel: #161b22; --line: #30363d;
              --text: #e6edf3; --muted: #8b949e;
              --ok: #3fb950; --bad: #f85149; --accent: #58a6ff;
            }
            * { box-sizing: border-box; }
            body {
              margin: 0; padding: 16px;
              background: var(--bg); color: var(--text);
              font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
              line-height: 1.5;
            }
            .wrap { max-width: 640px; margin: 0 auto; }
            h1 { font-size: 1.5rem; margin: 8px 0 4px; }
            h2 { font-size: 1.15rem; margin: 0 0 16px; font-weight: 600; }
            .muted { color: var(--muted); font-size: 0.875rem; }
            .card {
              background: var(--panel); border: 1px solid var(--line);
              border-radius: 12px; padding: 20px; margin-bottom: 16px;
            }
            .tag {
              display: inline-block; font-size: 0.75rem; color: var(--muted);
              border: 1px solid var(--line); border-radius: 999px;
              padding: 2px 10px; margin-bottom: 12px;
            }
            /* Siklar: telefonda parmakla basilacak kadar buyuk */
            .option { display: block; margin-bottom: 10px; }
            .option input { position: absolute; opacity: 0; }
            .option span {
              display: block; padding: 14px 16px;
              background: #0d1117; border: 1px solid var(--line);
              border-radius: 10px; cursor: pointer;
            }
            .option input:checked + span { border-color: var(--accent); background: #0d2137; }
            button, input[type=submit] {
              width: 100%; padding: 14px; font-size: 1rem; font-weight: 600;
              background: var(--accent); color: #04121f;
              border: 0; border-radius: 10px; cursor: pointer;
            }
            input[type=text], select {
              width: 100%; padding: 12px; margin-bottom: 14px; font-size: 1rem;
              background: #0d1117; color: var(--text);
              border: 1px solid var(--line); border-radius: 10px;
            }
            label.field { display: block; font-size: 0.875rem; color: var(--muted); margin-bottom: 6px; }
            .feedback { padding: 12px 16px; border-radius: 10px; margin-bottom: 16px; font-weight: 600; }
            .feedback.ok  { background: rgba(63,185,80,0.12);  color: var(--ok);  border: 1px solid rgba(63,185,80,0.4); }
            .feedback.bad { background: rgba(248,81,73,0.12);  color: var(--bad); border: 1px solid rgba(248,81,73,0.4); }
            .why { font-weight: 400; font-size: 0.875rem; margin-top: 8px; opacity: 0.85; }
            .timer { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 8px; }
            .timer b { font-size: 1.5rem; font-variant-numeric: tabular-nums; }
            .timer.hurry b { color: var(--bad); }
            .timebar { height: 6px; background: #0d1117; border-radius: 999px; overflow: hidden; margin-bottom: 16px; }
            .timebar > div { height: 100%; background: var(--ok); transition: width 1s linear; }
            .timebar.hurry > div { background: var(--bad); }
            .points { color: var(--ok); font-weight: 600; }
            .progress { height: 6px; background: #0d1117; border-radius: 999px; overflow: hidden; margin-bottom: 20px; }
            .progress > div { height: 100%; background: var(--accent); }
            .score { font-size: 2.5rem; font-weight: 700; text-align: center; margin: 8px 0; }
            table { width: 100%; border-collapse: collapse; }
            th, td { text-align: left; padding: 10px 6px; border-bottom: 1px solid var(--line); }
            th { color: var(--muted); font-weight: 500; font-size: 0.8rem; }
            td.num { text-align: right; font-variant-numeric: tabular-nums; }
            a { color: var(--accent); }
            .center { text-align: center; }
            """;
}
