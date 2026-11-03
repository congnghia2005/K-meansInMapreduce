#!/usr/bin/env bash
set -e

echo "======================================================="
echo "         BIÊN DỊCH ỨNG DỤNG K-MEANS MAPREDUCE"
echo "======================================================="

mkdir -p out

if ! command -v javac &> /dev/null; then
    echo "[LỖI] Không tìm thấy lệnh 'javac' trong PATH."
    echo "Vui lòng cài đặt OpenJDK 17+ và thiết lập JAVA_HOME."
    exit 1
fi

echo "Đang biên dịch mã nguồn từ src/ sang out/..."
javac -encoding UTF-8 -d out src/*.java

echo "[THÀNH CÔNG] Biên dịch hoàn tất!"

