# Kamp Quiz Motoru

Konsolda çalışan, Java ile sıfırdan yazılmış bilgi yarışması uygulaması.
Sorular dosyadan okunur, skorlar kaydedilir, lider tablosu tutulur.

**Katkıya açıktır** — Java bilmeden de soru paketi ekleyebilirsin.
Bkz. [CONTRIBUTING.md](CONTRIBUTING.md)

## Hızlı başlangıç

```bash
git clone https://github.com/efea0/java-demo-try.git
cd java-demo-try
./run.sh            # Windows'ta: run.bat
```

Tek gereksinim: **Java 17 veya üzeri** (`java -version` ile kontrol et).
Ek kütüphane, Maven, internet gerekmez.

## Neler yapıyor?

- `questions/` klasöründeki tüm `.txt` paketlerini otomatik yükler
- Kategori seçtirir, soru sayısını sorar, soruları karıştırır
- Yanlış girişleri affeder (harf girersen program çökmez, tekrar sorar)
- Cevap sonrası doğruyu gösterir
- Skoru `scores.txt`'ye kaydeder ve ilk 5'i listeler

## Proje yapısı

```
.
├── src/quiz/
│   ├── QuizApp.java        # giriş noktası (main)
│   ├── model/Question.java # soru verisi
│   ├── core/               # iş mantığı (ekranı bilmez)
│   │   ├── QuestionBank.java
│   │   ├── Quiz.java
│   │   └── Scoreboard.java
│   └── cli/ConsoleUI.java  # ekran ve klavye
├── questions/              # soru paketleri (katkı buraya)
├── run.sh / run.bat        # derle + çalıştır
└── CONTRIBUTING.md         # katkı rehberi
```

Mimari kuralı: **`core/` ekranı bilmez.** İş mantığı ile arayüz ayrıdır,
böylece aynı motor yarın web arayüzünde de çalışabilir.

## Yol haritası

- [x] Adım 1 — Proje iskeleti, ilk çalışan program
- [x] Adım 2 — `Question` sınıfı (OOP: sınıf, nesne, kapsülleme)
- [x] Adım 3 — Paket yapısı ve katmanlı mimari
- [x] Adım 4 — Klavyeden cevap alma, hatalı girdiye dayanıklılık
- [x] Adım 5 — Skor sistemi ve sonuç ekranı
- [x] Adım 6 — Soruları dosyadan okuma (katkı noktası)
- [x] Adım 7 — Lider tablosu (`scores.txt`)
- [x] Adım 8 — Katkı rehberi ve PR şablonu
- [ ] Adım 9 — Web sunucusu: telefondan bağlanılan canlı quiz

## Katkıda bulunmak

```bash
git checkout main && git pull origin main
git checkout -b soru/kendi-paketin
# questions/ altına .txt ekle
./run.sh
git add . && git commit -m "Açıklayıcı mesaj"
git push -u origin soru/kendi-paketin
```

Detaylar ve soru dosyası biçimi: [CONTRIBUTING.md](CONTRIBUTING.md)
