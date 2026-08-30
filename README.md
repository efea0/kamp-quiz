<img src="assets/banner.svg" alt="Kamp Quiz Motoru" width="880">

### Sınıfça oynanan bilgi yarışması — kendi ağınızda, hesapsız, kütüphanesiz

Hoca laptopunda bir komut çalıştırır. Öğrenciler telefonlarından bir QR okutup katılır.
Cevaplar geldikçe projeksiyondaki sıralama canlı döner.

**Gereken:** herkesin **aynı yerel ağda** olması (okul Wi-Fi'ı, ev modemi ya da bir telefonun
hotspot'u) ve hocanın bilgisayarında Java 17. Bu ağın **internete çıkması gerekmez** — quiz
trafiği modemin ötesine hiç gitmez, dışarıdaki hiçbir sunucuya bağlanılmaz.

```bash
git clone https://github.com/efea0/kamp-quiz.git
cd kamp-quiz
./run.sh web
```

> **Gereksinim: Java 17 veya üzeri — JDK, sadece JRE değil.**
> İkisinin de çalıştığını doğrula:
> ```
> java -version      → 17 ya da üzeri olmalı
> javac -version     → aynı sürümü göstermeli
> ```
> `javac` bulunamıyorsa sistemde yalnızca JRE (çalıştırıcı) var, derleyici yok.
> Kurulum ve PATH sorunları için aşağıdaki **Kurulum ayrıntıları** bölümüne bak.

<img src="assets/nasil-calisir.svg" alt="Nasıl çalışır: hoca sunucuyu başlatır, sınıf telefondan katılır, perdede canlı sıralama döner" width="880">

---

## Ekranlar

| Soru | Cevap ve nedeni | Sonuç |
|:---:|:---:|:---:|
| <img src="assets/ekran-soru.png" alt="Soru ekranı" width="230"> | <img src="assets/ekran-cevap.png" alt="Cevap değerlendirmesi" width="230"> | <img src="assets/ekran-ana.png" alt="Test seçme ekranı" width="230"> |
| Süre işlerken cevap ver. Hızlı cevap daha çok puan. | Doğru şık yeşil, seçtiğin yanlış şık kırmızı, altında **neden** o cevap. | 12 hazır test ya da kendi ayarların. |

| Projeksiyon | Yanlış raporu |
|:---:|:---:|
| <img src="assets/ekran-projeksiyon.png" alt="Canlı sıralama ekranı" width="380"> | <img src="assets/ekran-rapor.png" alt="Yanlış raporu" width="380"> |
| Katılım kodu, QR ve canlı sıralama. | Hangi soru kaç kişiyi düşürdü — en çok yanlıştan başlayarak. |

---

## Ne yapar

| | |
|---|---|
| 🔌 **İnternete çıkmaz** | Yerel ağ yeterli; modem internete bağlı olmasa bile çalışır. Veri dışarı gitmez |
| 👤 **Hesap gerekmez** | Adını yaz, katıl. Kayıt, şifre, e-posta yok |
| 📱 **Telefondan katılım** | 4 haneli oda kodu ya da QR. Uygulama indirmek gerekmez |
| ⏱ **Hız puanı** | Doğru cevap 500 puan, kalan süreye göre 500'e kadar bonus |
| 💡 **Açıklamalar** | Yanlış yapınca doğrusunu **ve nedenini** gösterir |
| 🔁 **Tekrar modu** | Sadece yanlışlarından oluşan ikinci bir tur |
| 📊 **Yanlış raporu** | Hocaya: hangi konuyu tekrar anlatmalı |
| 🧾 **Yoklama çıktısı** | Katılımcı, cevaplanan soru, doğru/yanlış ve doğruluk oranı CSV olarak indirilebilir |
| 🖥 **Projeksiyon dashboard'u** | Sayaç, süre, soru ilerlemesi, kalan soru, cevaplayan sayısı ve sıralama |
| 🔊 **Ses ve animasyon** | Kullanıcı etkileşimiyle etkinleşen ses efektleri ve projeksiyon geçişleri |
| 🖥 **İki arayüz** | Terminalde tek kişilik, tarayıcıda sınıfça — aynı motor |
| 🤖 **AI ile soru üretimi** | Konu yaz, paket üretsin; düzenle, onayla, kaydet |

**137 soru** · 12 kategori · **12 hazır test** · 373 otomatik denetim · **0 dış bağımlılık**

---

## Sınıfça oynamak — 3 adım

1. **Oda kur** → `/kur` sayfasından bir test seç, akış olarak `Senkron` seçersen sınıf aynı soruda ilerler
2. **Perdeye aç** → `/ekran?kod=1234`; dashboard, soru, süre, cevaplayan sayısı ve sıralama otomatik yenilenir
3. **Katıl** → öğrenciler kodu yazar ya da ekrandaki QR'ı okutur; QR oda kodunu Safari'de hazır taşır

Senkron modda puan ve sıralama, tüm katılımcılar cevap verince veya hoca **Cevabı göster** düğmesine basınca güncellenir.
Test bitince `/rapor?kod=1234` en çok yanlış yapılan soruları sıralar. Oda panelindeki CSV bağlantıları yoklama ve soru analiz dosyalarını indirir.

---

## Soru eklemek — katkının en kolay yolu

Java bilmene gerek yok. `questions/` klasörüne bir `.txt` dosyası ekle:

```
# baslik: Tarih

İstanbul hangi yıl fethedildi? | 1453 | 1071 | 1299 | 1923 | 1
> Fatih Sultan Mehmet döneminde, 29 Mayıs 1453'te fethedildi.
```

| Kural | |
|---|---|
| Ayırıcı | dikey çizgi `\|` |
| Son sütun | doğru şıkkın numarası — **1'den** başlar |
| `>` satırı | o sorunun açıklaması (isteğe bağlı ama çok değerli) |
| `#` satırı | yorum. `# baslik:` kategorinin görünen adı |

```bash
git checkout -b soru/tarih-paketi
./run.sh test
git commit -am "Tarih soru paketi eklendi"
git push -u origin soru/tarih-paketi
```

Her pull request'te testler otomatik çalışır. Ayrıntılar: **[CONTRIBUTING.md](CONTRIBUTING.md)**

### Koda katkı da kolaylaştırıldı

Web katmanı başlangıçta tek bir 1654 satırlık dosyaydı ve "bir butonu değiştireceğim"
diyen birini kapıda karşılıyordu. Şimdi her sayfa grubu kendi dosyasında:

| Değiştirmek istediğin | Dosya | Satır |
|---|---|---:|
| Renk, yazı tipi, buton biçimi | `web/Html.java` | 420 |
| Soru ve cevap ekranı | `web/QuizPages.java` | 436 |
| Oda paneli, projeksiyon, rapor | `web/RoomPages.java` | 344 |
| Ana sayfa ve katılım | `web/HomePages.java` | 251 |
| AI soru üretme sayfası | `web/GeneratePages.java` | 325 |
| Lider tablosu | `web/BoardPage.java` | 69 |
| Yeni sayfa eklemek | `web/WebServer.java` (rota tablosu) | 110 |
| Puanlama, süre, sıralama kuralları | `core/Quiz.java` | 198 |
| Soru dosyası biçimi | `core/QuestionBank.java` | 223 |

Tam liste ve her satırın açıklaması CONTRIBUTING.md'de. Sayfa sınıflarının hepsi
`ServerContext`'ten paylaşılan durumu (sorular, odalar, oturumlar) alır; yeni bir
sayfa eklemek kendi sınıfını yazıp rota tablosuna bir satır eklemek demektir.

---

<details>
<summary><b>Kurulum ayrıntıları</b> — Java yoksa, Windows/macOS/Linux</summary>

<br>

**JDK gerekiyor, JRE yetmez.** JRE program çalıştırır; JDK derler. Bize `javac` lazım.

| Sistem | Kurulum |
|---|---|
| **Windows** | `winget install Microsoft.OpenJDK.21` ya da [adoptium.net](https://adoptium.net) |
| **macOS** | `brew install openjdk@21` ya da [adoptium.net](https://adoptium.net) |
| **Debian/Ubuntu** | `sudo apt install openjdk-21-jdk` |
| **Fedora** | `sudo dnf install java-21-openjdk-devel` |

Kurduktan sonra **terminali kapatıp yeniden aç** — PATH ancak öyle güncellenir.

<b>Windows'ta kurdum ama `java -version` hâlâ eski sürümü gösteriyor</b>

Eski bir Java, yeni kurulanın önünde kalmıştır. Hangi kopyaların olduğunu gör:

```powershell
where.exe java
where.exe javac
Get-ChildItem "C:\Program Files\Microsoft" -Directory
```

Yeni JDK'yı o oturum için öne al (klasör adını yukarıdaki çıktıdan al):

```powershell
$jdk = "C:\Program Files\Microsoft\jdk-21.0.12.7-hotspot\bin"
$env:Path = "$jdk;" + $env:Path
javac -version
```

Kalıcı yapmak için: **Ayarlar → Sistem → Sistem bilgileri → Gelişmiş sistem ayarları
→ Ortam Değişkenleri** → `Path` içinde yeni JDK satırını yukarı taşı.

Çalıştırma: Windows'ta `run.bat`, macOS/Linux'ta önce bir kez `chmod +x run.sh`, sonra `./run.sh`.

Betik kullanmadan:

```bash
javac -encoding UTF-8 -d out $(find src -name "*.java")
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out quiz.QuizApp
```

Komutlar: `./run.sh` (terminal oyunu) · `./run.sh web [port]` (sunucu) · `./run.sh test` (denetimler)
</details>

<details>
<summary><b>Telefonlar bağlanamıyorsa</b> — güvenlik duvarı, IP bulma, ağ yalıtımı</summary>

<br>

| Belirti | Çözüm |
|---|---|
| Windows'ta uyarı penceresi çıktı | Güvenlik duvarı — **"Özel ağlarda izin ver"** işaretle |
| macOS'ta bağlantı yok | Sistem Ayarları → Ağ → Güvenlik Duvarı → gelen bağlantılara izin |
| Linux'ta bağlantı yok | `sudo ufw allow 8080/tcp` |
| Hiçbiri olmuyor | Ağ **client isolation** kullanıyor olabilir (okul Wi-Fi'larında yaygın). Telefonun hotspot'unu aç, laptopu ona bağla |
| `Address already in use` | Port meşgul: `./run.sh web 9000` |

IP adresini bulmak: `ipconfig` (Windows) · `ipconfig getifaddr en0` (macOS) · `hostname -I` (Linux)
</details>

<details>
<summary><b>AI ile soru üretme</b> — kurulum ve anahtar güvenliği</summary>

<br>

`/uret` sayfasından konu yazıp paket ürettirebilirsin. Üretilen taslak **doğrudan quize girmez**:
düzenlenebilir hâlde gelir, elle ya da "AI ile düzenle" kutusuyla değiştirilir, kaydetmeden önce
biçimi doğrulanır.

**API anahtarı koda yazılmaz.** En güvenli yol, anahtarı sadece kendine okunur bir dosyaya koymak:

```bash
mkdir -p ~/.config/kamp-quiz
printf '%s' "ANAHTARIN" > ~/.config/kamp-quiz/gemini.key
chmod 600 ~/.config/kamp-quiz/gemini.key
```

OpenRouter için dosya adı `openrouter.key`. **İkisi de varsa önce Gemini denenir; kota dolduğunda
OpenRouter'a otomatik geçilir.** Ortam değişkeni de çalışır (`GEMINI_API_KEY`) ama `export` satırı
kabuk geçmişine düşer, bu yüzden dosya daha güvenlidir.

```bash
export QUIZ_ADMIN_KEY="birparola"
export OPENROUTER_MODEL="saglayici/model"
```

Anahtar tanımlı değilse uygulama normal çalışır, yalnızca `/uret` kapalı görünür.
Depoda anahtar bulunmaz — projeye katkı veren kimse göremez.
</details>

<details>
<summary><b>Mimari</b> — dosyalar ve değişmez kural</summary>

<br>

<img src="assets/mimari.svg" alt="Katmanlı mimari" width="880">

```
src/quiz/
├── QuizApp.java            giriş noktası, parçaları bağlar
├── model/Question.java     soru verisi, kendi geçerliliğini korur
├── core/                   iş mantığı — ekranı hiç bilmez
│   ├── QuestionBank.java   questions/*.txt okur, bozuk satırı atlar
│   ├── Quiz.java           sıra, süre, puan, cevap geçmişi
│   ├── QuizSet.java        hazır test tanımı
│   └── Scoreboard.java     scores.txt'ye yazar, sıralar
├── cli/ConsoleUI.java      terminal arayüzü
├── web/                    tarayıcı arayüzü
│   ├── WebServer.java      JDK'nın HttpServer'ı, oturumlar, odalar
│   ├── QrCode.java         sıfırdan QR kodlayıcı
│   └── Html.java           sayfa şablonu ve stil
├── ai/QuestionGenerator.java   Gemini / OpenRouter ile soru üretimi
└── test/SelfTest.java      kendi kendini sınama
```

**Değişmez kural:** `core/` ve `model/` paketlerinde tek bir `System.out.println` yoktur —
ekranı hiç tanımazlar. Bozuk bir soru satırı bulunduğunda uyarı ekrana basılmaz, çağıran arayüze
**liste olarak döndürülür**; onu nasıl göstereceğine terminal ya da web arayüzü karar verir.

Kural yazıyla kalmıyor: `./run.sh test` kaynak kodu okuyup bu paketlerde `System.out` arıyor.
Kuralı bozan bir katkı testte kırmızı görünür.

Karşılığını web arayüzünü eklerken aldık: terminal ve tarayıcı **aynı motoru** paylaşıyor,
`Quiz` ve `Scoreboard` sınıflarına tek satır dokunulmadı.
</details>

<details>
<summary><b>Davranış notları</b> — kenar durumlarda ne olur</summary>

<br>

- Bozuk bir soru satırı quizi çökertmez; atlanır ve uyarı basılır
- Sayı yerine harf girilirse program çökmez, soruyu tekrar sorar
- Sayfayı yenilemek geri sayımı sıfırlamaz — süre soru başına bir kez başlar
- Süre dolduğunda cevap sunucu tarafında yanlış sayılır; tarayıcıyı kandırmak işe yaramaz
- Cevap gönderimi POST-Redirect-GET kullanır: yenilemek cevabı tekrar göndermez
- Tekrar turu oda sıralamasını etkilemez
- Tüm dosya okuma/yazma UTF-8'e sabitlenmiştir
- 25 eşzamanlı oyuncuyla denendi: 25/25 tamamlandı, hata yok
</details>

---

## Yol haritası

| | |
|---|---|
| ✔ | Katmanlı mimari, dosyadan soru okuma, skor ve lider tablosu |
| ✔ | Web arayüzü, oda kodu, projeksiyon ekranı, QR ile katılım |
| ✔ | Süre sınırı, hız puanı, açıklamalar, tekrar modu, yanlış raporu |
| ✔ | Hazır test setleri, bölüm bazlı kategori mix'leri, AI ile soru üretme, kendi kendini test |
| ✔ | Senkron canlı mod — herkes aynı soruda, hoca ilerletir |
| ✔ | Projeksiyon dashboard'u, ses/animasyon, QR ile oda kodlu katılım |
| ✔ | Yoklama ve soru analizi için CSV çıktıları |
| ☐ | Takım modu |

## Katkı

Soru paketi, yeni özellik, hata düzeltmesi — hepsi açık.
Başlamadan önce **[CONTRIBUTING.md](CONTRIBUTING.md)** dosyasına bak.
