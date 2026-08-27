# Kamp Quiz Motoru

Konsolda calisan, Java ile yazilmis bilgi yarismasi uygulamasi.
Kamp boyunca adim adim gelistiriliyor ve **katkiya aciktir**.

## Gereksinimler

- Java 17 veya uzeri (`java -version` ile kontrol et)

## Calistirma

Linux / macOS / Git Bash:

```bash
./run.sh
```

Windows (cmd veya PowerShell):

```
javac -d out src\QuizApp.java
java -cp out QuizApp
```

## Proje yapisi

```
.
├── src/          # Java kaynak kodlari (elle yazdigimiz her sey burada)
├── out/          # Derlenmis .class dosyalari (otomatik uretilir, repoya girmez)
├── run.sh        # Derle + calistir kisayolu
└── README.md
```

## Yol haritasi

- [x] Adim 1 - Proje iskeleti ve ilk calisan program
- [ ] Adim 2 - `Soru` sinifi (OOP)
- [ ] Adim 3 - Soru listesi
- [ ] Adim 4 - Kullanicidan cevap alma
- [ ] Adim 5 - Skor sistemi
- [ ] Adim 6 - Sorulari dosyadan okuma
- [ ] Adim 7 - Lider tablosu
- [ ] Adim 8 - Katki rehberi

## Katkida bulunmak

Bu proje herkesin branch acip katki verebilecegi sekilde tasarlaniyor.
Detayli rehber Adim 8'de eklenecek. Ozet akis:

```bash
git checkout main
git pull origin main
git checkout -b ozellik/senin-ozelligin
# ... degisiklikleri yap ...
git add .
git commit -m "Aciklayici bir mesaj"
git push -u origin ozellik/senin-ozelligin
```

Sonrasinda GitHub uzerinden Pull Request acilir.
