@echo off
chcp 65001 > nul
echo =======================================================
echo          BIEN DICH UNG DUNG K-MEANS MAPREDUCE
echo =======================================================

if not exist out mkdir out

REM Kiem tra xem javac co ton tai khong
where javac >nul 2>nul
if %errorlevel% neq 0 (
    echo [LOI] Khong tim thay lenh 'javac' trong PATH.
    echo Vui long cai dat JDK 17+ hoac thiet lap JAVA_HOME.
    exit /b 1
)

echo Dang bien dich cac tap tin ma nguon tu src/...
javac -encoding UTF-8 -d out src/*.java

if %errorlevel% equ 0 (
    echo [THANH CONG] Bien dich hoan tat vao thu muc out/
) else (
    echo [THAT BAI] Qua trinh bien dich gap loi!
    exit /b 1
)

