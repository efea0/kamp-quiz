#!/usr/bin/env bash
# Projeyi derleyip calistirir.  Kullanim: ./run.sh
set -e

echo "[1/2] Derleniyor..."
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name "*.java")

echo "[2/2] Calistiriliyor..."
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out quiz.QuizApp
