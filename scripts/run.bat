@echo off
chcp 65001 > nul
echo =======================================================
echo              CHAY UNG DUNG K-MEANS MAPREDUCE
echo =======================================================

REM JVM Memory tuning: cap phat heap vua phai (vi doc streaming nen chi can 1-2GB du cho ca file 10GB)
set JVM_OPTS=-Xms512m -Xmx2048m -Dfile.encoding=UTF-8

REM Neu khong truyen tham so nao, mac dinh in huong dan
if "%~1"=="" (
    echo Cach su dung:
    echo   1. Sinh du lieu mau:
    echo      run.bat generate [so_diem] [so_chieu] [so_cum] [ten_file]
    echo      Vi du: scripts\run.bat generate 1000000 10 5 points.csv
    echo.
    echo   2. Chay song song (Parallel):
    echo      scripts\run.bat parallel [ten_file] [k] [dim] [threads] [splits]
    echo      Vi du: scripts\run.bat parallel points.csv 5 10 8 16
    echo.
    echo   3. Chay tuan tu (Sequential):
    echo      scripts\run.bat sequential [ten_file] [k] [dim]
    echo      Vi du: scripts\run.bat sequential points.csv 5 10
    echo.
    echo   4. Chay do kiem hieu nang (Benchmark):
    echo      scripts\run.bat benchmark [ten_file] [k] [dim] [splits] [runs]
    echo      Vi du: scripts\run.bat benchmark points.csv 5 10 16 3
    exit /b 0
)

if "%~1"=="generate" (
    set N=%~2
    if "%N%"=="" set N=1000000
    set D=%~3
    if "%D%"=="" set D=10
    set K=%~4
    if "%K%"=="" set K=5
    set OUT=%~5
    if "%OUT%"=="" set OUT=points.csv
    
    java %JVM_OPTS% -cp out DataGenerator --output %OUT% --n %N% --dim %D% --k %K% --stddev 1.5 --seed 42
    exit /b 0
)

if "%~1"=="parallel" (
    set INPUT=%~2
    if "%INPUT%"=="" set INPUT=points.csv
    set K=%~3
    if "%K%"=="" set K=5
    set DIM=%~4
    if "%DIM%"=="" set DIM=10
    set THREADS=%~5
    if "%THREADS%"=="" set THREADS=8
    set SPLITS=%~6
    if "%SPLITS%"=="" set SPLITS=16

    java %JVM_OPTS% -cp out KMeansMR --input %INPUT% --k %K% --dim %DIM% --threads %THREADS% --splits %SPLITS% --mode parallel
    exit /b 0
)

if "%~1"=="sequential" (
    set INPUT=%~2
    if "%INPUT%"=="" set INPUT=points.csv
    set K=%~3
    if "%K%"=="" set K=5
    set DIM=%~4
    if "%DIM%"=="" set DIM=10

    java %JVM_OPTS% -cp out KMeansMR --input %INPUT% --k %K% --dim %DIM% --mode sequential
    exit /b 0
)

if "%~1"=="benchmark" (
    set INPUT=%~2
    if "%INPUT%"=="" set INPUT=points.csv
    set K=%~3
    if "%K%"=="" set K=5
    set DIM=%~4
    if "%DIM%"=="" set DIM=10
    set SPLITS=%~5
    if "%SPLITS%"=="" set SPLITS=16
    set RUNS=%~6
    if "%RUNS%"=="" set RUNS=3

    java %JVM_OPTS% -cp out KMeansMR --input %INPUT% --k %K% --dim %DIM% --splits %SPLITS% --runs %RUNS% --mode benchmark
    exit /b 0
)

REM Chay tuy bien voi moi tham so truyen vao
java %JVM_OPTS% -cp out KMeansMR %*

