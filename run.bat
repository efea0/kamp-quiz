@echo off
REM Windows icin: projeyi derleyip calistirir.  Kullanim: run.bat
chcp 65001 >nul
echo [1/2] Derleniyor...
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
del sources.txt
if errorlevel 1 goto :error
echo [2/2] Calistiriliyor...
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out quiz.QuizApp
goto :eof
:error
echo Derleme hatasi! Yukaridaki mesaji oku.
