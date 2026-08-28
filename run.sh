#!/usr/bin/env bash
# Projeyi derleyip calistirir.  Kullanim: ./run.sh
set -e

# ------------------------------------------------------------------
# JDK denetimi: javac yoksa, veya surumu cok eskiyse, derlemeye hic
# girmeden anlasilir bir mesajla dur.
# ------------------------------------------------------------------

os_name="$(uname -s 2>/dev/null || echo unknown)"

print_install_hint() {
  echo
  case "$os_name" in
    Darwin)
      echo "  macOS:   brew install openjdk@21"
      echo "           (Homebrew yoksa https://adoptium.net adresinden JDK 21 indirin.)"
      ;;
    Linux)
      echo "  Linux:   sudo apt install openjdk-21-jdk"
      echo "           (Fedora/RHEL: sudo dnf install java-21-openjdk-devel)"
      ;;
    *)
      echo "  JDK 21 kurun: https://adoptium.net"
      ;;
  esac
  echo
}

if ! command -v javac >/dev/null 2>&1; then
  echo
  if command -v java >/dev/null 2>&1; then
    echo "HATA: Java var ama JDK yok — geliştirici sürümü gerekiyor."
    echo "'java' çalışıyor ama derleyici olan 'javac' bulunamadı (muhtemelen sadece JRE kurulu)."
  else
    echo "HATA: Java bulunamadı ('java' ve 'javac' çalıştırılamadı)."
  fi
  echo
  echo "Çözüm: JDK 21 kurun."
  print_install_hint
  exit 1
fi

# javac -version ciktisi genelde "javac 1.8.0_501" ya da "javac 21.0.10"
# bicimindedir; bazi ortamlarda oncesinde baska satirlar (uyarilar vb.)
# gelebilir, bu yuzden "javac " ile baslayan satiri ariyoruz.
javac_version_line="$(javac -version 2>&1 | grep '^javac ' || true)"
javac_version="${javac_version_line#javac }"
javac_version="$(printf '%s' "$javac_version" | tr -d '[:space:]')"

javac_major="${javac_version%%.*}"
if [ "$javac_major" = "1" ]; then
  # Eski numaralandirma: "1.8.0_501" -> asil surum ikinci basamak (8).
  rest="${javac_version#*.}"
  javac_major="${rest%%.*}"
fi

case "$javac_major" in
  ''|*[!0-9]*)
    echo
    echo "HATA: 'javac -version' çıktısı anlaşılamadı: ${javac_version_line:-<boş>}"
    echo "Elle kontrol edin: javac -version"
    echo
    exit 1
    ;;
esac

if [ "$javac_major" -lt 17 ]; then
  echo
  echo "HATA: Kurulu JDK sürümü çok eski (bulunan: $javac_version)."
  echo "Bu proje en az Java 17 gerektirir."
  echo
  echo "Çözüm: JDK 21 kurun."
  print_install_hint
  exit 1
fi

echo "[1/2] Derleniyor..."
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name "*.java")

# ./run.sh test  -> kendi kendini test eder
if [ "$1" = "test" ]; then
  echo "[2/2] Testler calisiyor..."
  java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out quiz.test.SelfTest
  exit $?
fi

echo "[2/2] Calistiriliyor..."
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out quiz.QuizApp "$@"
