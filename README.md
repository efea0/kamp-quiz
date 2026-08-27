<img src="assets/banner.svg" alt="Kamp Quiz Motoru" width="880">

Konsolda çalışan bilgi yarışması. Java ile, hiçbir dış kütüphane kullanmadan,
sıfırdan yazılıyor. Sorular düz metin dosyalarında durur — koda dokunmadan
yeni soru paketi eklenebilir.

```bash
git clone https://github.com/efea0/java-demo-try.git
cd java-demo-try
./run.sh          # Windows: run.bat
```

Tek gereksinim **Java 17 veya üzeri**. Maven yok, Gradle yok, internet gerekmez.

---

## Nasıl görünüyor

Aşağıdaki çıktı gerçek bir oturumdan alınmıştır:

```
=========================================
         KAMP QUIZ MOTORU  v1.0
=========================================

81 soru yüklendi.

Adın nedir? (boş bırak = Misafir): Efe

Kategoriler:
   0) Hepsi karışık
   1) Donanım Temelleri
   2) Genel Kültür
   3) Linux Temelleri
   4) Matematik
   5) Ağ Temelleri
   6) Özgür Yazılım
   7) Yazılım Temelleri
Seçimin: 5
Kaç soru sorulsun? (1-14): 3

Soru 1/3   [Ağ Temelleri]
Cihazlara otomatik IP adresi dağıtan servis hangisidir?

   1) DNS
   2) DHCP
   3) FTP
   4) SSH

Cevabın (1-4): 2
  [+] DOĞRU!

...

=========================================
  SONUÇ - Efe
  Skor: 2/3  (%67)
  Fena değil, biraz daha çalışma.
=========================================

*** LIDER TABLOSU ***
-----------------------------------------
  1. Efe              2/3   %67   27.08.2026 21:53
```

---

## Soru paketleri

Kamp müfredatına göre hazırlandı. Toplam **81 soru**, hepsi giriş seviyesi.

| Paket | Dosya | Soru | İçerik |
|---|---|---:|---|
| Ağ Temelleri | `network.txt` | 14 | IP, DNS, port, TCP/UDP, DHCP, SSH |
| Linux Temelleri | `linux.txt` | 15 | temel komutlar, dizin yapısı, izinler, sudo |
| Donanım Temelleri | `donanim.txt` | 14 | CPU, RAM, depolama, anakart, portlar |
| Özgür Yazılım | `ozgur-yazilim.txt` | 14 | GNU/GPL, copyleft, Krita, GIMP, Blender |
| Yazılım Temelleri | `yazilim-temelleri.txt` | 10 | Java, Git, temel kavramlar |
| Genel Kültür | `genel-kultur.txt` | 8 | coğrafya, sanat, bilim |
| Matematik | `matematik.txt` | 6 | dört işlem, geometri, üs |

Yeni paket eklemek bir `.txt` dosyası yazmak kadar kolay:

```
# baslik: Siber Güvenlik
Kimlik doğrulamada ikinci adıma ne denir? | 2FA | VPN | DNS | SSL | 1
```

Biçim ve kurallar: **[CONTRIBUTING.md](CONTRIBUTING.md)**

---

## Mimari

<img src="assets/mimari.svg" alt="Katmanlı mimari şeması" width="880">

Proje üç katmana ayrılmıştır ve bu ayrım kasıtlıdır: `quiz.core` içinde tek bir
`System.out.println` yoktur. İş mantığı ekranı tanımadığı için, konsol arayüzünün
yerine tarayıcı arayüzü koyduğumuzda motor kodu hiç değişmeyecek.

```
src/quiz/
├── QuizApp.java            giriş noktası, parçaları bağlar
├── model/Question.java     soru verisi, kendi geçerliliğini korur
├── core/
│   ├── QuestionBank.java   questions/*.txt okur, bozuk satırı atlar
│   ├── Quiz.java           sıra, karıştırma, skor
│   └── Scoreboard.java     scores.txt'ye yazar, sıralar
└── cli/ConsoleUI.java      ekran ve klavye
```

## Davranış notları

- Bozuk bir soru satırı quizi çökertmez; o satır atlanır ve uyarı basılır
- Sayı yerine harf girilirse program çökmez, soruyu tekrar sorar
- Sorular her oturumda karıştırılır
- Skorlar `scores.txt`'ye eklenir, eskiler silinmez
- Tüm dosya okuma/yazma işlemleri UTF-8'e sabitlenmiştir

## Yol haritası

| | Adım | Konu |
|---|---|---|
| ✔ | 1 | Proje iskeleti, ilk çalışan program |
| ✔ | 2 | `Question` sınıfı — sınıf, nesne, kapsülleme |
| ✔ | 3 | Paket yapısı ve katmanlı mimari |
| ✔ | 4 | Klavye girdisi ve hatalı girdiye dayanıklılık |
| ✔ | 5 | Skor sistemi |
| ✔ | 6 | Soruların dosyadan okunması |
| ✔ | 7 | Lider tablosu |
| ✔ | 8 | Katkı rehberi ve PR şablonu |
| ☐ | 9 | Web arayüzü — telefondan bağlanılan canlı quiz |

## Katkı

Java bilmeden de katkı verebilirsin — bir soru paketi eklemek 5 dakika sürer.

```bash
git checkout main && git pull origin main
git checkout -b soru/kendi-paketin
# questions/ altına .txt ekle, ./run.sh ile test et
git commit -am "Siber güvenlik soru paketi eklendi"
git push -u origin soru/kendi-paketin
```

Ayrıntılar: [CONTRIBUTING.md](CONTRIBUTING.md)
