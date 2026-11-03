#!/usr/bin/env bash
set -e

JVM_OPTS="-Xms512m -Xmx2048m -Dfile.encoding=UTF-8"

if [ -z "$1" ]; then
    echo "Cách sử dụng:"
    echo "  1. Sinh dữ liệu mẫu:"
    echo "     ./scripts/run.sh generate [số_điểm] [số_chiều] [số_cụm] [tên_file]"
    echo "     Ví dụ: ./scripts/run.sh generate 1000000 10 5 points.csv"
    echo ""
    echo "  2. Chạy song song (Parallel):"
    echo "     ./scripts/run.sh parallel [tên_file] [k] [dim] [threads] [splits]"
    echo "     Ví dụ: ./scripts/run.sh parallel points.csv 5 10 8 16"
    echo ""
    echo "  3. Chạy tuần tự (Sequential):"
    echo "     ./scripts/run.sh sequential [tên_file] [k] [dim]"
    echo "     Ví dụ: ./scripts/run.sh sequential points.csv 5 10"
    echo ""
    echo "  4. Chạy đo kiểm hiệu năng (Benchmark):"
    echo "     ./scripts/run.sh benchmark [tên_file] [k] [dim] [splits] [runs]"
    echo "     Ví dụ: ./scripts/run.sh benchmark points.csv 5 10 16 3"
    exit 0
fi

CMD="$1"
shift

case "$CMD" in
    generate)
        N="${1:-1000000}"
        D="${2:-10}"
        K="${3:-5}"
        OUT="${4:-points.csv}"
        java $JVM_OPTS -cp out DataGenerator --output "$OUT" --n "$N" --dim "$D" --k "$K" --stddev 1.5 --seed 42
        ;;
    parallel)
        INPUT="${1:-points.csv}"
        K="${2:-5}"
        DIM="${3:-10}"
        THREADS="${4:-8}"
        SPLITS="${5:-16}"
        java $JVM_OPTS -cp out KMeansMR --input "$INPUT" --k "$K" --dim "$DIM" --threads "$THREADS" --splits "$SPLITS" --mode parallel
        ;;
    sequential)
        INPUT="${1:-points.csv}"
        K="${2:-5}"
        DIM="${3:-10}"
        java $JVM_OPTS -cp out KMeansMR --input "$INPUT" --k "$K" --dim "$DIM" --mode sequential
        ;;
    benchmark)
        INPUT="${1:-points.csv}"
        K="${2:-5}"
        DIM="${3:-10}"
        SPLITS="${4:-16}"
        RUNS="${5:-3}"
        java $JVM_OPTS -cp out KMeansMR --input "$INPUT" --k "$K" --dim "$DIM" --splits "$SPLITS" --runs "$RUNS" --mode benchmark
        ;;
    *)
        java $JVM_OPTS -cp out KMeansMR "$CMD" "$@"
        ;;
esac

