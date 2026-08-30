@echo off
REM Windows icin: projeyi derleyip calistirir.  Kullanim: run.bat
chcp 65001 >nul
echo [1/2] Derleniyor...
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
set "COMPILE_EXIT=%ERRORLEVEL%"
del sources.txt
if not "%COMPILE_EXIT%"=="0" goto :error
if "%1"=="test" goto :test
echo [2/2] Calistiriliyor...
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out quiz.QuizApp %*
set "EXIT_CODE=%ERRORLEVEL%"
exit /b %EXIT_CODE%
:test
echo [2/2] Testler calisiyor...
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out quiz.test.SelfTest
set "EXIT_CODE=%ERRORLEVEL%"
exit /b %EXIT_CODE%
:error
echo Derleme hatasi! Yukaridaki mesaji oku.
exit /b 1