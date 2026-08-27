<img src="assets/banner.svg" alt="Kamp Quiz Motoru" width="880">

Bilgi yarışması motoru. Java ile, hiçbir dış kütüphane kullanmadan sıfırdan yazılıyor.
İki şekilde oynanır: **terminalde** tek kişilik, ya da **web modunda** — aynı Wi-Fi'daki
herkes telefonundan katılır. Sorular düz metin dosyalarında durur, koda dokunmadan
yeni paket eklenebilir.

---

## Kurulum ve çalıştırma

Tek gereksinim **Java 17 veya üzeri**. Maven yok, Gradle yok, internet gerekmez.

### Java kurulu mu?

Her üç sistemde de aynı komut:

```
java -version
```

`command not found` diyorsa Java yok. Kurulumu:

| Sistem | Komut / yöntem |
|---|---|
| **Windows** | `winget install Microsoft.OpenJDK.21` — ya da [adoptium.net](https://adoptium.net) üzerinden `.msi` indir |
| **macOS** | `brew install openjdk@21` — Homebrew yoksa [adoptium.net](https://adoptium.net) üzerinden `.pkg` indir |
| **Linux (Debian/Ubuntu)** | `sudo apt install openjdk-21-jdk` |
| **Linux (Fedora)** | `sudo dnf install java-21-openjdk-devel` |

### Projeyi indir

Üç sistemde de aynı:

```
git clone https://github.com/efea0/java-demo-try.git
cd java-demo-try
```

### Çalıştır

**Windows** (PowerShell veya cmd):

```
run.bat
```

**macOS ve Linux:**

```bash
chmod +x run.sh    # sadece ilk seferde
./run.sh
```

> **macOS notu:** ilk çalıştırmada `zsh: permission denied` alırsan `chmod +x run.sh` komutunu atlamışsındır.

Betikleri kullanmak istemezsen elle de derleyebilirsin:

```bash
# macOS / Linux
javac -encoding UTF-8 -d out $(find src -name "*.java")
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out quiz.QuizApp
```

```
:: Windows
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out quiz.QuizApp
```

---

## Web modu — telefondan katılım

Quiz'i yerel ağda yayınlar. Aynı Wi-Fi'daki herkes tarayıcıdan katılabilir.

```bash
./run.sh web          # macOS / Linux
run.bat web           # Windows
./run.sh web 9000     # port meşgulse başka port
```

Sunucu açılışta bağlantı adreslerini ekrana yazar:

```
=========================================
  SUNUCU ÇALIŞIYOR
=========================================
  Bu bilgisayarda : http://localhost:8080
  Aynı Wi-Fi'dan  : http://192.168.1.42:8080

  Katılımcılar yukarıdaki adresi tarayıcıya yazsın.
  Durdurmak için: Ctrl+C
=========================================
```

Katılımcılar telefonlarından `http://192.168.1.42:8080` adresine girer, adını yazar,
kategori seçer ve oynar. Herkesin oturumu ayrıdır — farklı kategori, farklı soru,
farklı hız. Skorlar ortak lider tablosunda toplanır.

### IP adresini elle bulmak

Sunucu adresi yazmazsa:

| Sistem | Komut |
|---|---|
| **Windows** | `ipconfig` → "IPv4 Address" satırı |
| **macOS** | `ipconfig getifaddr en0` (Wi-Fi) veya `ipconfig getifaddr en1` |
| **Linux** | `ip addr show` veya `hostname -I` |

### Sunum modu — oda kodu

Sınıfça oynamak için:

1. Hoca `/kur` sayfasından bir test seçer, **4 haneli oda kodu** üretilir
2. `/ekran?kod=XXXX` projeksiyona açılır — canlı sıralama 3 saniyede bir yenilenir
3. Katılımcılar ana sayfada kodu ve adını yazıp katılır

Herkes aynı testten sorulur ama **soru seçimi ve sırası kişiye özeldir** —
yan yana oturanlar birbirinin ekranından kopyalayamaz.

### Bağlanamıyorlarsa

| Belirti | Sebep ve çözüm |
|---|---|
| Windows'ta ilk açılışta uyarı penceresi | Güvenlik duvarı izni — **"Özel ağlarda izin ver"** işaretle |
| macOS'ta bağlantı yok | Sistem Ayarları → Ağ → Güvenlik Duvarı → gelen bağlantılara izin ver |
| Linux'ta bağlantı yok | `sudo ufw allow 8080/tcp` |
| Hiçbiri işe yaramıyor | Ağ **client isolation** kullanıyor olabilir (okul/kurum Wi-Fi'larında yaygın). Telefonun hotspot'unu aç, laptopu ona bağla, tekrar dene |
| `Address already in use` | Port meşgul — `./run.sh web 9000` |

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
> Şifreye ek olarak ikinci bir doğrulama gerekir: SMS kodu, uygulama ya da donanım anahtarı.
```

`>` ile başlayan satır o sorunun açıklamasıdır; cevaptan sonra oyuncuya gösterilir.

Biçim ve kurallar: **[CONTRIBUTING.md](CONTRIBUTING.md)**

---

## Mimari

<img src="assets/mimari.svg" alt="Katmanlı mimari şeması" width="880">

Proje katmanlara ayrılmıştır ve bu ayrım kasıtlıdır: `quiz.core` içinde tek bir
`System.out.println` yoktur. İş mantığı ekranı tanımadığı için, konsol arayüzü ile
web arayüzü **aynı motoru** kullanır — `Quiz` ve `Scoreboard` sınıflarına web modu
için tek satır eklenmedi.

```
src/quiz/
├── QuizApp.java            giriş noktası, parçaları bağlar
├── model/Question.java     soru verisi, kendi geçerliliğini korur
├── core/
│   ├── QuestionBank.java   questions/*.txt okur, bozuk satırı atlar
│   ├── Quiz.java           sıra, karıştırma, skor
│   └── Scoreboard.java     scores.txt'ye yazar, sıralar
├── cli/ConsoleUI.java      konsol arayüzü
└── web/
    ├── WebServer.java      HTTP yönlendirme, oturumlar
    ├── Html.java           sayfa şablonu ve stil
    └── GameSession.java    tek oyuncunun web durumu
```

## Puanlama

Wayground'daki gibi hız ödüllendirilir:

| | Puan |
|---|---|
| Doğru cevap | 500 taban puan |
| Hız bonusu | Kalan süreye orantılı, en fazla +500 |
| Yanlış cevap | 0 |
| Süre dolması | 0 — cevap yanlış sayılır |

Soru başına en fazla **1000 puan**. Süre web modunda seçilir (10 / 20 / 45 saniye)
ve ekranda geri sayım çubuğu döner; süre bitince cevap otomatik gönderilir.

Lider tablosu önce puana, eşitlikte doğru yüzdesine göre sıralanır.

## Davranış notları

- Bozuk bir soru satırı quizi çökertmez; o satır atlanır ve uyarı basılır
- Sayı yerine harf girilirse program çökmez, soruyu tekrar sorar
- Sorular her oturumda karıştırılır
- Skorlar `scores.txt`'ye eklenir, eskiler silinmez
- Tüm dosya okuma/yazma işlemleri UTF-8'e sabitlenmiştir
- Web modunda her oyuncunun oturumu ayrıdır; aynı anda onlarca kişi oynayabilir
- Cevap gönderimi POST-Redirect-GET desenini kullanır: sayfa yenilenince cevap tekrar gitmez
- Sayfayı yenilemek geri sayımı sıfırlamaz; süre soru başına bir kez başlar
- Açıklaması olan sorularda cevaptan sonra "neden" metni gösterilir

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
| ✔ | 9 | Web arayüzü — telefondan bağlanılan canlı quiz |
| ✔ | 10 | Süre sınırı, hız puanı ve açıklama alanı |
| ✔ | 11 | Oda kodu ve projeksiyon ekranı |
| ☐ | 12 | AI ile soru paketi üretme |

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
