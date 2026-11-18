@echo off
chcp 65001 > nul
echo =======================================================
echo     VE BIEU DO HIEU NANG VA KET QUA PHAN CUM K-MEANS
echo =======================================================

set "PYTHON_CMD=python"
if exist "C:\Users\OS\AppData\Local\Programs\Python\Python313\python.exe" (
    set "PYTHON_CMD=C:\Users\OS\AppData\Local\Programs\Python\Python313\python.exe"
)

if not exist results.csv goto CHECK_CLUSTERS

echo [1/2] Dang ve bieu do hieu nang: Speedup, Efficiency, Time, Throughput...
"%PYTHON_CMD%" plot_results.py %*

:CHECK_CLUSTERS
if not exist points_2M_28D.csv goto FINISH

echo.
echo [2/2] Dang ve bieu do truc quan hoa ket qua phan cum: PCA 2D, Sai lech tam, Ty le cac cum...
"%PYTHON_CMD%" plot_clusters.py

:FINISH
echo.
echo ================================================================================
echo [THANH CONG] Tat ca bieu do da duoc luu trong thu muc: charts\
echo ================================================================================
