#!/usr/bin/env bash
# Projeyi derleyip calistirir.
# Kullanim: ./run.sh
set -e   # herhangi bir komut hata verirse hemen dur

echo "[1/2] Derleniyor..."
mkdir -p out
javac -d out $(find src -name "*.java")

echo "[2/2] Calistiriliyor..."
echo
java -cp out QuizApp
