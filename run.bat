@echo off
REM Windows icin: projeyi derleyip calistirir.  Kullanim: run.bat
chcp 65001 >nul

REM ------------------------------------------------------------------
REM JDK denetimi: javac yoksa, veya surumu cok eskiyse, derlemeye hic
REM girmeden anlasilir bir mesajla dur.
REM ------------------------------------------------------------------

where javac >nul 2>nul
if errorlevel 1 goto :nojdk

REM javac -version ciktisi genelde "javac 1.8.0_501" ya da "javac 21.0.10"
REM bicimindedir; bazi ortamlarda oncesinde baska satirlar gelebilir, bu
REM yuzden ilk sozcugu "javac" olan satiri ariyoruz.
set "JAVAC_VER="
for /f "tokens=1,2" %%a in ('javac -version 2^>^&1') do (
  if "%%a"=="javac" set "JAVAC_VER=%%b"
)

if not defined JAVAC_VER goto :versioncheckfail

REM Surum ana numarasini cikar: "1.8.0_501" -> 8   |   "21.0.10" -> 21
for /f "delims=. tokens=1,2" %%a in ("%JAVAC_VER%") do (
  if "%%a"=="1" (set "JAVA_MAJOR=%%b") else (set "JAVA_MAJOR=%%a")
)

echo %JAVA_MAJOR%| findstr /r "^[0-9][0-9]*$" >nul
if errorlevel 1 goto :versioncheckfail

if %JAVA_MAJOR% LSS 17 goto :oldjdk

echo [1/2] Derleniyor...
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
set "BUILD_ERR=%errorlevel%"
del sources.txt
if not "%BUILD_ERR%"=="0" goto :buildfail

if "%1"=="test" goto :test
echo [2/2] Calistiriliyor...
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out quiz.QuizApp %*
goto :eof

:test
echo [2/2] Testler calisiyor...
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out quiz.test.SelfTest
goto :eof

:buildfail
echo.
echo Derleme hatasi! Yukaridaki mesaji oku.
exit /b 1

:nojdk
where java >nul 2>nul
if errorlevel 1 goto :nojava
echo.
echo HATA: Java var ama JDK yok - gelistirici surumu gerekiyor.
echo 'java' calisiyor ama derleyici olan 'javac' bulunamadi (muhtemelen sadece JRE kurulu).
echo.
echo Cozum: JDK 21 kurun:
echo     winget install Microsoft.OpenJDK.21
echo.
echo Kurulumdan sonra PowerShell'i (veya CMD'yi) KAPATIP YENIDEN ACIN,
echo yoksa PATH guncellemesi bu pencerede gorunmez.
exit /b 1

:nojava
echo.
echo HATA: Java bulunamadi ('java' ve 'javac' calistirilamadi).
echo.
echo Cozum: JDK 21 kurun:
echo     winget install Microsoft.OpenJDK.21
echo.
echo Kurulumdan sonra PowerShell'i (veya CMD'yi) KAPATIP YENIDEN ACIN,
echo yoksa PATH guncellemesi bu pencerede gorunmez.
exit /b 1

:oldjdk
echo.
echo HATA: Kurulu JDK surumu cok eski (bulunan: %JAVAC_VER%).
echo Bu proje en az Java 17 gerektirir.
echo.
echo Cozum: JDK 21 kurun:
echo     winget install Microsoft.OpenJDK.21
echo.
echo Kurulumdan sonra PowerShell'i (veya CMD'yi) KAPATIP YENIDEN ACIN,
echo yoksa PATH guncellemesi bu pencerede gorunmez.
exit /b 1

:versioncheckfail
echo.
echo HATA: 'javac -version' ciktisi anlasilamadi.
echo Elle kontrol edin: javac -version
exit /b 1
