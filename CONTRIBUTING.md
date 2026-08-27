# Katkı Rehberi

Bu projeye katkı vermek için Java bilmen **gerekmiyor**. En kolay katkı yolu
yeni bir soru paketi eklemek — 5 dakika sürer.

---

## 1. En kolay katkı: yeni soru paketi ekle

### Adım adım

```bash
# 1) Projeyi bilgisayarına indir (bir kez yapılır)
git clone https://github.com/efea0/java-demo-try.git
cd java-demo-try

# 2) Her zaman güncel main'den başla
git checkout main
git pull origin main

# 3) Kendine bir dal (branch) aç
git checkout -b soru/tarih-paketi

# 4) questions/ klasörüne yeni bir .txt dosyası ekle
#    (aşağıdaki biçime bak)

# 5) Test et - senin soruların yükleniyor mu?
./run.sh

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
Cumhuriyet hangi yıl ilan edildi? | 1920 | 1921 | 1923 | 1938 | 3
```

**Kurallar:**

| Kural | Açıklama |
|---|---|
| Ayırıcı | Dikey çizgi `\|` |
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
└── cli/
    └── ConsoleUI.java # Ekrana basma, klavyeden okuma
```

**Altın kural:** `core/` içine **asla** `System.out.println` yazma.
İş mantığı ekranı bilmemeli. Böylece aynı mantığı yarın web arayüzünde
de kullanabiliriz.

### Kod yazım kuralları

- Sınıf adları `BuyukHarfle` (PascalCase): `QuestionBank`
- Metot ve değişken adları `kucukHarfle` (camelCase): `getQuestionNumber`
- Sabitler `BUYUK_HARFLE`: `QUESTIONS_DIR`
- Girinti: 4 boşluk (tab değil)
- Kod İngilizce, yorumlar Türkçe

### Göndermeden önce

```bash
./run.sh          # derleniyor ve çalışıyor mu?
git status        # istemediğin dosya eklenmiş mi?
```

`out/` ve `scores.txt` repoya **girmemeli** (zaten `.gitignore`'da).

---

## 3. Yeni fikir mi var?

Kod yazmadan önce bir **Issue** aç, konuşalım. Aynı işi iki kişi yapmasın.

## 4. Sık karşılaşılan sorunlar

| Sorun | Çözüm |
|---|---|
| `./run.sh: Permission denied` | `chmod +x run.sh` |
| Türkçe karakterler bozuk görünüyor | Windows'ta `run.bat` kullan (o `chcp 65001` yapıyor) |
| `Soru klasörü bulunamadı` | Programı projenin ana klasöründen çalıştır |
| `git push` reddedildi | Önce `git pull origin main` yapıp çakışmaları çöz |
