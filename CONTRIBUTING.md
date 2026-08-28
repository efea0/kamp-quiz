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
│   ├── QuestionBank.java  # Dosyadan soru okuma (questions/*.txt)
│   ├── QuizSetLoader.java # Hazır test okuma (sets/*.txt)
│   ├── QuizSet.java       # Hazır test tanımı
│   ├── Quiz.java          # Quiz mantığı, puanlama
│   └── Scoreboard.java    # Lider tablosu
├── cli/
│   └── ConsoleUI.java # Terminal arayüzü
├── web/
│   ├── WebServer.java     # Sadece sunucu kurulumu + rota tablosu
│   ├── ServerContext.java # Paylaşılan durum (sorular, odalar, oturumlar) + ortak yardımcılar
│   ├── Room.java          # Oda kodu ve katılımcılar
│   ├── GameSession.java   # Tek oyuncunun web durumu
│   ├── QrCode.java        # Sıfırdan QR kodlayıcı
│   ├── Html.java          # Sayfa şablonu ve CSS
│   ├── HomePages.java     # /  /ayarla  /start  /katil
│   ├── QuizPages.java     # /quiz  /cevap  /devam  /sonuc  /tekrar
│   ├── RoomPages.java     # /kur  /oda  /ekran  /rapor
│   ├── BoardPage.java     # /tablo
│   └── GeneratePages.java # /uret
├── ai/                    # Soru üretimi (Gemini / OpenRouter)
└── test/SelfTest.java     # Kendi kendini sınama
```

Her sayfa sınıfı `ServerContext`'i alır; paylaşılan durum (sorular, odalar,
oturumlar) ve ortak yardımcılar (form okuma, yönlendirme, çerez) oradadır.
Yeni bir sayfa eklemek için kendi sınıfını yaz ve `WebServer`'daki rota
tablosuna bir satır ekle.

**API anahtarları:** hiçbir anahtar depoya girmez. Anahtar ya
`~/.config/kamp-quiz/gemini.key` gibi bir dosyadan (izin 600) ya da ortam
değişkeninden okunur. Koda, örnek dosyaya veya teste anahtar yazan bir PR
kabul edilmez.

### Ne değiştirmek istiyorsan hangi dosyaya bakacaksın

| İstediğin değişiklik | Dosya | Zorluk |
|---|---|---|
| Yeni soru eklemek | `questions/*.txt` | çok kolay |
| Hazır test tanımlamak | `sets/*.txt` | çok kolay |
| Soru dosyası biçimini değiştirmek | `core/QuestionBank.java` | orta |
| Hazır test dosyası biçimini değiştirmek (`# sure:`, `# zorluk:` gibi satırlar) | `core/QuizSetLoader.java` | orta |
| Puanlama kuralını değiştirmek (taban puan, hız bonusu) | `core/Quiz.java` | orta |
| Lider tablosunun nasıl kaydedildiğini/okunduğunu değiştirmek | `core/Scoreboard.java` | orta |
| Terminal (konsol) arayüzünü değiştirmek | `cli/ConsoleUI.java` | orta |
| Renk/yazı tipi değiştirmek | `web/Html.java` (CSS `Html.CSS` içinde) | kolay |
| Hangi URL'nin hangi sayfaya gittiğini değiştirmek, yeni bir rota eklemek | `web/WebServer.java` | kolay |
| Oturum/çerez, oda haritası gibi paylaşılan durumu değiştirmek | `web/ServerContext.java` | orta |
| Ana sayfayı, "kendin ayarla" ya da oda kodu ile katılımı değiştirmek | `web/HomePages.java` | orta |
| Soru ekranını, cevap sonrası ekranı ya da sonuç ekranını değiştirmek | `web/QuizPages.java` | orta |
| Hoca panelini, projeksiyon ekranını ya da yanlış raporunu değiştirmek | `web/RoomPages.java` | orta |
| Lider tablosu sayfasının görünümünü değiştirmek | `web/BoardPage.java` | kolay |
| AI ile soru üretme/düzenleme akışını değiştirmek | `web/GeneratePages.java` | orta |
| AI sağlayıcısını (Gemini/OpenRouter) ya da istemi değiştirmek | `ai/QuestionGenerator.java` | zor |
| QR kod üretimini değiştirmek | `web/QrCode.java` | zor |
| Senkron oda akışının (soru/cevap fazları) mantığını değiştirmek | `web/Room.java` | zor |
| Soru nesnesine yeni bir alan eklemek | `model/Question.java` | zor (birçok yeri etkiler) |

**Altın kural:** `core/` ve `model/` içine **asla** `System.out.println` yazma.
Bu paketler ekranı bilmez. Kullanıcıya bir şey söylemen gerekiyorsa mesajı
bir `List<String>` uyarı listesine ekle; onu ekrana basmak arayüzün işi.

Bu kural denetleniyor: `./run.sh test` kaynak kodu okuyup `core` ve `model`
paketlerinde `System.out` arıyor. Bulursa test kırmızı olur, PR birleşmez.

Kuralın karşılığı şu: web arayüzü eklenirken `Quiz` ve `Scoreboard`
sınıflarına tek satır dokunulmadı.

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
