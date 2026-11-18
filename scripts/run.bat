@echo off
chcp 65001 > nul

REM Tu dong tim Java 17+ tren may tinh
set JAVA_CMD=java
if exist "C:\Program Files\Java\jdk-17\bin\java.exe" (
    set "JAVA_CMD=C:\Program Files\Java\jdk-17\bin\java.exe"
) else if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
    )
)

set JVM_OPTS=-Xms512m -Xmx2048m -Dfile.encoding=UTF-8

REM Dieu huong lenh theo tham so dau tien
if "%~1"=="" goto PRINT_HELP
if /i "%~1"=="generate"   goto DO_GENERATE
if /i "%~1"=="parallel"   goto DO_PARALLEL
if /i "%~1"=="sequential" goto DO_SEQUENTIAL
if /i "%~1"=="benchmark"  goto DO_BENCHMARK
if /i "%~1"=="sizes"      goto DO_SIZES

REM Neu truyen tham so tuy bien khac (vi du --input ...)
"%JAVA_CMD%" %JVM_OPTS% -cp out KMeansMR %*
exit /b %errorlevel%

:DO_GENERATE
set "N=%~2"
if "%N%"=="" set "N=1000000"
set "D=%~3"
if "%D%"=="" set "D=10"
set "K=%~4"
if "%K%"=="" set "K=5"
set "OUT=%~5"
if "%OUT%"=="" set "OUT=points.csv"

echo [RUN] Dang sinh %N% diem, %D% chieu, %K% cum vao tep %OUT%...
"%JAVA_CMD%" %JVM_OPTS% -cp out DataGenerator --output "%OUT%" --n %N% --dim %D% --k %K% --stddev 1.5 --seed 42
exit /b %errorlevel%

:DO_PARALLEL
set "INPUT=%~2"
if "%INPUT%"=="" set "INPUT=points.csv"
set "K=%~3"
if "%K%"=="" set "K=5"
set "DIM=%~4"
if "%DIM%"=="" set "DIM=10"
set "THREADS=%~5"
if "%THREADS%"=="" set "THREADS=8"
set "SPLITS=%~6"
if "%SPLITS%"=="" set "SPLITS=16"

echo [RUN] Chay Song song tren tep %INPUT% (K=%K%, Dim=%DIM%, Threads=%THREADS%, Splits=%SPLITS%)...
"%JAVA_CMD%" %JVM_OPTS% -cp out KMeansMR --input "%INPUT%" --k %K% --dim %DIM% --threads %THREADS% --splits %SPLITS% --mode parallel
exit /b %errorlevel%

:DO_SEQUENTIAL
set "INPUT=%~2"
if "%INPUT%"=="" set "INPUT=points.csv"
set "K=%~3"
if "%K%"=="" set "K=5"
set "DIM=%~4"
if "%DIM%"=="" set "DIM=10"

echo [RUN] Chay Tuan tu tren tep %INPUT% (K=%K%, Dim=%DIM%)...
"%JAVA_CMD%" %JVM_OPTS% -cp out KMeansMR --input "%INPUT%" --k %K% --dim %DIM% --mode sequential
exit /b %errorlevel%

:DO_BENCHMARK
set "INPUT=%~2"
if "%INPUT%"=="" set "INPUT=points.csv"
set "K=%~3"
if "%K%"=="" set "K=5"
set "DIM=%~4"
if "%DIM%"=="" set "DIM=10"
set "SPLITS=%~5"
if "%SPLITS%"=="" set "SPLITS=16"
set "RUNS=%~6"
if "%RUNS%"=="" set "RUNS=3"

echo [RUN] Chay Benchmark tren tep %INPUT% (K=%K%, Dim=%DIM%, Splits=%SPLITS%, Runs=%RUNS%)...
"%JAVA_CMD%" %JVM_OPTS% -cp out KMeansMR --input "%INPUT%" --k %K% --dim %DIM% --splits %SPLITS% --runs %RUNS% --mode benchmark
exit /b %errorlevel%

:DO_SIZES
set "SIZES=%~2"
if "%SIZES%"=="" set "SIZES=100K,500K,1M"
set "K=%~3"
if "%K%"=="" set "K=5"
set "DIM=%~4"
if "%DIM%"=="" set "DIM=10"
set "RUNS=%~5"
if "%RUNS%"=="" set "RUNS=3"

echo [RUN] Chay Benchmark da kich thuoc: %SIZES% (K=%K%, Dim=%DIM%, Runs=%RUNS%)...
"%JAVA_CMD%" %JVM_OPTS% -cp out KMeansMR --k %K% --dim %DIM% --splits 16 --runs %RUNS% --mode benchmark --sizes "%SIZES%"
exit /b %errorlevel%

:PRINT_HELP
echo =======================================================
echo              CHAY UNG DUNG K-MEANS MAPREDUCE
echo =======================================================
echo Cach su dung:
echo   1. Sinh du lieu mau [Generate]:
echo      scripts\run.bat generate [so_diem] [so_chieu] [so_cum] [ten_file]
echo      Vi du: scripts\run.bat generate 2000000 10 5 points.csv
echo.
echo   2. Chay song song [Parallel]:
echo      scripts\run.bat parallel [ten_file] [k] [dim] [threads] [splits]
echo      Vi du: scripts\run.bat parallel points.csv 5 10 8 16
echo.
echo   3. Chay tuan tu [Sequential]:
echo      scripts\run.bat sequential [ten_file] [k] [dim]
echo      Vi du: scripts\run.bat sequential points.csv 5 10
echo.
echo   4. Chay do kiem don tep [Benchmark]:
echo      scripts\run.bat benchmark [ten_file] [k] [dim] [splits] [runs]
echo      Vi du: scripts\run.bat benchmark points.csv 5 10 16 3
echo.
echo   5. Chay do kiem da kich thuoc [--sizes]:
echo      scripts\run.bat sizes [ds_kich_thuoc] [k] [dim] [runs]
echo      Vi du: scripts\run.bat sizes 100K,500K,1M 5 10 3
echo.
echo   6. Ve bieu do do thi:
echo      plot.bat
exit /b 0
