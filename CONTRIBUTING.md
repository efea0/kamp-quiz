# Katkı Rehberi

Bu projeye katkı vermek için Java bilmen **gerekmiyor**. En kolay katkı yolu
yeni bir soru paketi eklemek — 5 dakika sürer.

---

## 1. En kolay katkı: yeni soru paketi ekle

### Adım adım

```bash
# 1) Projeyi bilgisayarına indir (bir kez yapılır)
git clone https://github.com/efea0/kamp-quiz.git
cd kamp-quiz

# 2) Her zaman güncel main'den başla
git checkout main
git pull origin main

# 3) Kendine bir dal (branch) aç
git checkout -b soru/tarih-paketi

# 4) questions/ klasörüne yeni bir .txt dosyası ekle
#    (aşağıdaki biçime bak)

# 5) Test et - senin soruların yükleniyor mu?
./run.sh          # Windows'ta: run.bat

# 6) Değişikliği kaydet ve gönder
git add questions/tarih.txt
git commit -m "Tarih soru paketi eklendi (10 soru)"
git push -u origin soru/tarih-paketi
```

Sonra GitHub'da çıkan **"Compare & pull request"** butonuna bas ve PR aç.

### Soru dosyası biçimi

Dosya adı: `questions/konu-adi.txt` (küçük harf, Türkçe karakter yok, boşluk yerine tire)

```
# baslik: Tarih
# Biçim:  Soru metni | şık1 | şık2 | şık3 | şık4 | doğruNo

İstanbul hangi yıl fethedildi? | 1453 | 1071 | 1299 | 1923 | 1
> Fatih Sultan Mehmet döneminde, 29 Mayıs 1453'te fethedildi.

Cumhuriyet hangi yıl ilan edildi? | 1920 | 1921 | 1923 | 1938 | 3
> 29 Ekim 1923. 1920 TBMM'nin açılışı, 1938 ise Atatürk'ün vefatıdır.
```

### Açıklama satırı (`>`)

Bir sorunun altına `>` ile başlayan satır eklersen, cevaptan sonra
oyuncuya gösterilir. **Zorunlu değil ama şiddetle tavsiye edilir** —
quizi sınavdan derse çeviren şey budur.

İyi bir açıklama şunu yapar:
- Doğru cevabın **nedenini** söyler ("çünkü...")
- Sık karıştırılan şıkkı ayırt eder ("X ise şu işe yarar")
- Bir cümle, en fazla iki cümle olur

**Kurallar:**

| Kural | Açıklama |
|---|---|
| Ayırıcı | Dikey çizgi `\|` |
| Açıklama | Sorunun altına `>` ile başlayan satır (isteğe bağlı) |
| Şık sayısı | En az 2, en fazla istediğin kadar |
| `doğruNo` | **1'den** başlar. `1` = ilk şık |
| Yorum satırı | `#` ile başlar, program bunu atlar |
| `# baslik:` | Kategorinin ekranda görünecek adı |
| Soru metninde `\|` | **Kullanma** — ayırıcıyla karışır |
| Dosya kodlaması | UTF-8 (Türkçe karakterler için) |

Bozuk bir satır tüm quizi çökertmez; program o satırı atlar ve uyarı basar.

---

## 2. Koda katkı

### Dal (branch) isimlendirme

| Ön ek | Ne zaman | Örnek |
|---|---|---|
| `soru/` | Yeni soru paketi | `soru/cografya-paketi` |
| `ozellik/` | Yeni özellik | `ozellik/sure-siniri` |
| `duzeltme/` | Hata düzeltme | `duzeltme/bos-girdi-hatasi` |

### Proje yapısı — nereye ne yazılır?

```
src/quiz/
├── QuizApp.java       # Giriş noktası. Parçaları birbirine bağlar.
├── model/
│   └── Question.java  # Veri taşıyan sınıflar. İş mantığı YOK.
├── core/
│   ├── QuestionBank.java  # Dosyadan soru okuma
│   ├── Quiz.java          # Quiz mantığı, skor
│   └── Scoreboard.java    # Lider tablosu
├── cli/
│   └── ConsoleUI.java # Terminal arayüzü
├── web/
│   ├── WebServer.java     # HTTP yönlendirme, oturum ve oda yönetimi
│   ├── Room.java          # Oda kodu ve katılımcılar
│   ├── QrCode.java        # Sıfırdan QR kodlayıcı
│   ├── Html.java          # Sayfa şablonu ve CSS
│   └── GameSession.java   # Tek oyuncunun web durumu
├── ai/                    # Soru üretimi (Gemini / OpenRouter)
└── test/SelfTest.java     # Kendi kendini sınama
```

**API anahtarları:** hiçbir anahtar depoya girmez. Ortam değişkeninden okunur
(`GEMINI_API_KEY`, `OPENROUTER_API_KEY`). Koda anahtar yazan bir PR kabul edilmez.

**Altın kural:** `core/` içine **asla** `System.out.println` yazma.
İş mantığı ekranı bilmemeli. Bu kural sayesinde web arayüzü eklenirken
`Quiz` ve `Scoreboard` sınıflarına tek satır dokunulmadı.

### Kod yazım kuralları

- Sınıf adları `BuyukHarfle` (PascalCase): `QuestionBank`
- Metot ve değişken adları `kucukHarfle` (camelCase): `getQuestionNumber`
- Sabitler `BUYUK_HARFLE`: `QUESTIONS_DIR`
- Girinti: 4 boşluk (tab değil)
- Kod İngilizce, yorumlar Türkçe

### Göndermeden önce

```bash
./run.sh test     # ZORUNLU — 200'den fazla denetim, hepsi geçmeli
./run.sh          # elle de bir tur oyna
./run.sh web      # web modu da açılıyor mu?
git status        # istemediğin dosya eklenmiş mi?
```

`./run.sh test` kırmızı veriyorsa gönderme. Hangi denetimin kaldığını yazar.

`out/` ve `scores.txt` repoya **girmemeli** (zaten `.gitignore`'da).

---

## 3. Yeni fikir mi var?

Kod yazmadan önce bir **Issue** aç, konuşalım. Aynı işi iki kişi yapmasın.

## 4. Sık karşılaşılan sorunlar

| Sorun | Sistem | Çözüm |
|---|---|---|
| `Permission denied` | macOS/Linux | `chmod +x run.sh` |
| `zsh: command not found: java` | macOS | `brew install openjdk@21` |
| `'javac' is not recognized` | Windows | JDK kurulu değil ya da PATH'te yok — `winget install Microsoft.OpenJDK.21` |
| Türkçe karakterler bozuk | Windows | `run.bat` kullan (`chcp 65001` yapıyor) |
| `Soru klasörü bulunamadı` | hepsi | Programı projenin ana klasöründen çalıştır |
| `Address already in use` | hepsi | Port meşgul: `./run.sh web 9000` |
| Telefon bağlanamıyor | hepsi | Güvenlik duvarı izni ver; olmazsa telefon hotspot'u üzerinden dene |
| `git push` reddedildi | hepsi | Önce `git pull origin main` yapıp çakışmaları çöz |
