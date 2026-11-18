@echo off
chcp 65001 > nul
echo =======================================================
echo          BIEN DICH UNG DUNG K-MEANS MAPREDUCE
echo =======================================================

if not exist out mkdir out

REM Tu dong tim JDK 17+ tren may tinh
set JAVAC_CMD=javac
if exist "C:\Program Files\Java\jdk-17\bin\javac.exe" (
    set "JAVAC_CMD=C:\Program Files\Java\jdk-17\bin\javac.exe"
) else if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javac.exe" (
        set "JAVAC_CMD=%JAVA_HOME%\bin\javac.exe"
    )
)

echo Trinh bien dich dang dung: "%JAVAC_CMD%"
"%JAVAC_CMD%" -version

echo.
echo Dang bien dich cac tap tin ma nguon tu src/...
"%JAVAC_CMD%" -encoding UTF-8 -d out src/*.java

if %errorlevel% equ 0 (
    echo.
    echo [THANH CONG] Bien dich hoan tat vao thu muc out/!
) else (
    echo.
    echo [THAT BAI] Qua trinh bien dich gap loi!
    exit /b 1
)
