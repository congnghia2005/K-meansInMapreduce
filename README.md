# PHÂN CỤM DỮ LIỆU LỚN BẰNG K-MEANS THEO MÔ HÌNH MAPREDUCE (JAVA THUẦN)

Bài tập lớn môn học **Xử lý song song**  
Cài đặt hoàn toàn bằng Java thuần (JDK 17+), không sử dụng Hadoop, Spark, Maven dependency hay thư viện bên ngoài.

---

## 1. Cấu trúc thư mục

```
d:\xlss\
├── src\
│   ├── Split.java             # Quản lý khoảng byte [startByte, endByte)
│   ├── SplitReader.java       # Đọc luồng theo byte offset, căn chỉnh ranh giới dòng
│   ├── PartialResult.java     # Kết quả tích lũy In-mapper Combiner (zero-lock)
│   ├── MapTask.java           # Tác vụ Mapper gán cụm và gom cục bộ
│   ├── ReduceTask.java        # Tác vụ Reducer tính trọng tâm mới và độ dịch chuyển
│   ├── SequentialKMeans.java  # Thuật toán K-means tuần tự (Baseline đối chứng)
│   ├── Benchmark.java         # Bộ đo kiểm tốc độ, tính Speedup, Hiệu suất, đối soát
│   ├── DataGenerator.java     # Trình sinh dữ liệu tổng hợp hàng triệu dòng (streaming)
│   └── KMeansMR.java          # Chương trình chính điều phối MapReduce & CLI
├── scripts\
│   ├── compile.bat            # Script biên dịch trên Windows
│   ├── run.bat                # Script chạy các chế độ trên Windows
│   ├── compile.sh             # Script biên dịch trên Linux/macOS
│   └── run.sh                 # Script chạy trên Linux/macOS
├── out\                       # Thư mục chứa bytecode .class sau khi biên dịch
├── results.csv                # File kết quả đo kiểm hiệu năng (sau khi chạy benchmark)
└── README.md                  # Hướng dẫn chi tiết
```

---

## 2. Yêu cầu môi trường

- **Hệ điều hành**: Windows 10/11, Linux (Ubuntu, CentOS, Debian) hoặc macOS.
- **Java Development Kit**: JDK 17 trở lên (đã kiểm thử với OpenJDK 17/21).
- **Phần cứng khuyến nghị**: CPU đa nhân (4 - 16 cores), tối thiểu 4GB RAM.

---

## 3. Hướng dẫn biên dịch và thực thi

### 3.1. Biên dịch

**Trên Windows:**
```cmd
javac -encoding UTF-8 -d out src/*.java
:: Hoặc chạy script:
scripts\compile.bat
```

**Trên Linux / macOS:**
```bash
chmod +x scripts/*.sh
./scripts/compile.sh
```

---

### 3.2. Sinh dữ liệu thử nghiệm (DataGenerator)

Chương trình hỗ trợ sinh dữ liệu dạng streaming trực tiếp ra đĩa, không tốn RAM dù sinh 10 triệu điểm:

```cmd
:: Sinh 1,000,000 điểm, 10 chiều, 5 cụm:
java -cp out DataGenerator --output points.csv --n 1000000 --dim 10 --k 5 --stddev 1.5 --seed 42

:: Hoặc dùng script:
scripts\run.bat generate 1000000 10 5 points.csv
```

---

### 3.3. Các chế độ chạy ứng dụng (KMeansMR)

#### Chế độ 1: Chạy song song MapReduce (`--mode parallel`)
```cmd
java -Xmx2g -Dfile.encoding=UTF-8 -cp out KMeansMR --input points.csv --k 5 --dim 10 --threads 8 --splits 16 --maxIter 20 --eps 1e-4 --mode parallel
```

#### Chế độ 2: Chạy tuần tự (`--mode sequential`)
```cmd
java -Xmx2g -Dfile.encoding=UTF-8 -cp out KMeansMR --input points.csv --k 5 --dim 10 --mode sequential --maxIter 20 --eps 1e-4
```

#### Chế độ 3: Chạy đo kiểm toàn diện (`--mode benchmark`)
Tự động chạy tuần tự và song song trên các mức luồng 1, 2, 4, 8, ... đến tối đa số nhân CPU; lặp lại mỗi cấu hình; in bảng Speedup & Hiệu suất; kiểm tra sai khác kết quả và xuất ra `results.csv`.
```cmd
java -Xmx2g -Dfile.encoding=UTF-8 -cp out KMeansMR --input points.csv --k 5 --dim 10 --splits 16 --runs 3 --mode benchmark
```

---

## 4. Bảng tham số dòng lệnh đầy đủ

| Tham số | Ý nghĩa | Mặc định |
| :--- | :--- | :--- |
| `--input <path>` | Đường dẫn file CSV dữ liệu đầu vào | `points.csv` |
| `--k <int>` | Số lượng cụm phân nhóm ($K$) | `5` |
| `--dim <int>` | Số chiều vector đặc trưng ($D$) | `10` |
| `--threads <int>` | Số luồng xử lý song song | Số nhân CPU máy |
| `--splits <int>` | Số phân đoạn byte chia nhỏ tệp | `threads * 2` |
| `--maxIter <int>` | Số vòng lặp tối đa | `20` |
| `--eps <double>` | Ngưỡng sai số dịch chuyển tâm để dừng | `1e-4` |
| `--skipCols <int>`| Số cột đầu bỏ qua (VD: nhãn nhị phân HIGGS = 1) | `0` |
| `--mode <str>` | Chế độ chạy: `parallel` \| `sequential` \| `benchmark` | `parallel` |
| `--seed <long>` | Seed ngẫu nhiên để tái lập tâm ban đầu | `42` |
| `--runs <int>` | Số lần lặp đo kiểm trong chế độ benchmark | `3` |
| `--verbose` | In thông tin chi tiết từng mapper ở mọi vòng | `false` |

