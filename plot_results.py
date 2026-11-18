#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
plot_results.py: Script vẽ biểu đồ đánh giá hiệu năng thuật toán K-means MapReduce
từ tệp dữ liệu kết quả results.csv.

Đầu ra:
1. chart_speedup.png    : Biểu đồ Gia tốc (Speedup vs Số luồng) kèm đường lý tưởng
2. chart_efficiency.png : Biểu đồ Hiệu suất (Efficiency % vs Số luồng)
3. chart_time.png       : Biểu đồ Thời gian thực thi (ms) kèm thanh sai số (±StdDev)
4. chart_throughput.png : Biểu đồ Thông lượng xử lý (M điểm/giây và MB/s)
5. chart_dashboard.png  : Bảng tổng hợp 4 trong 1 (Dashboard) chất lượng cao 300 DPI
"""

import os
import sys

# Thiết lập thư mục cấu hình tạm thời cho matplotlib tránh lỗi quyền ghi trên Windows
os.environ.setdefault('MPLCONFIGDIR', os.path.join(os.environ.get('TEMP', '.'), 'matplotlib_cache'))

import csv
import argparse
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker

# Cấu hình font chữ và hiển thị tiếng Việt / Unicode chuẩn
plt.rcParams['font.sans-serif'] = ['Segoe UI', 'DejaVu Sans', 'Arial', 'Calibri']
plt.rcParams['axes.unicode_minus'] = False
plt.rcParams['figure.autolayout'] = True


def load_data(csv_path):
    if not os.path.exists(csv_path):
        print(f"[LỖI] Không tìm thấy tệp {csv_path}")
        sys.exit(1)

    records = []
    with open(csv_path, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            try:
                rec = {
                    'DatasetSize': row.get('DatasetSize', '').replace('"', '').strip(),
                    'Points': int(row.get('Points', 0)),
                    'Configuration': row.get('Configuration', '').replace('"', '').strip(),
                    'Threads': int(row.get('Threads', 1)),
                    'AvgTimeMs': float(row.get('AvgTimeMs', 0.0)),
                    'StdDevMs': float(row.get('StdDevMs', 0.0)),
                    'MinTimeMs': float(row.get('MinTimeMs', 0.0)),
                    'Speedup': float(row.get('Speedup', 1.0)),
                    'EfficiencyPercent': float(row.get('EfficiencyPercent', 100.0)),
                    'ThroughputMPointsSec': float(row.get('ThroughputMPointsSec', 0.0)),
                    'ThroughputMBSec': float(row.get('ThroughputMBSec', 0.0)),
                    'Verified': row.get('Verified', 'true').lower() == 'true'
                }
                records.append(rec)
            except Exception as e:
                print(f"[CẢNH BÁO] Bỏ qua dòng lỗi: {row} ({e})")
    return records


def group_by_dataset(records):
    grouped = {}
    for r in records:
        key = r['DatasetSize'] if r['DatasetSize'] else f"{r['Points']:,} điểm"
        if key not in grouped:
            grouped[key] = []
        grouped[key].append(r)
    return grouped


def plot_speedup(dataset_name, rows, out_dir):
    # Lọc chỉ lấy các cấu hình song song (sắp xếp theo Threads)
    par_rows = [r for r in rows if r['Threads'] >= 1 and 'Song song' in r['Configuration']]
    if not par_rows:
        par_rows = rows
    par_rows.sort(key=lambda x: x['Threads'])

    threads = [r['Threads'] for r in par_rows]
    speedups = [r['Speedup'] for r in par_rows]

    fig, ax = plt.subplots(figsize=(8, 5), dpi=300)
    # Đường lý thuyết Speedup = Threads (Linear Speedup)
    max_t = max(threads) if threads else 16
    ax.plot([1, max_t], [1, max_t], 'k--', alpha=0.6, label='Tuyến tính lý tưởng (Linear: S = p)')

    # Đường Speedup thực tế
    ax.plot(threads, speedups, 'o-', color='#1f77b4', linewidth=2.5, markersize=8, label='Speedup thực nghiệm ($S_p$)')

    for t, s in zip(threads, speedups):
        ax.annotate(f"{s:.2f}x", (t, s), textcoords="offset points", xytext=(0, 10), ha='center',
                    fontsize=9, fontweight='bold', color='#1f77b4')

    ax.set_title(f'Độ tăng tốc (Speedup) theo số luồng - Tập dữ liệu: {dataset_name}', fontsize=12, fontweight='bold', pad=12)
    ax.set_xlabel('Số luồng xử lý (Threads - $p$)', fontsize=11, fontweight='bold')
    ax.set_ylabel('Hệ số gia tốc (Speedup - $S_p$)', fontsize=11, fontweight='bold')
    ax.set_xticks(threads)
    ax.grid(True, linestyle=':', alpha=0.6)
    ax.legend(frameon=True, facecolor='white', framealpha=0.9)

    out_file = os.path.join(out_dir, f'chart_speedup_{dataset_name}.png')
    plt.savefig(out_file, bbox_inches='tight')
    plt.close()
    return out_file


def plot_efficiency(dataset_name, rows, out_dir):
    par_rows = [r for r in rows if r['Threads'] >= 1 and 'Song song' in r['Configuration']]
    if not par_rows:
        par_rows = rows
    par_rows.sort(key=lambda x: x['Threads'])

    threads = [r['Threads'] for r in par_rows]
    efficiencies = [r['EfficiencyPercent'] for r in par_rows]

    fig, ax = plt.subplots(figsize=(8, 5), dpi=300)
    # Đường 100% lý tưởng
    ax.axhline(100, color='r', linestyle='--', alpha=0.6, label='Hiệu suất lý tưởng (100%)')

    # Cột hiệu suất
    bars = ax.bar([str(t) for t in threads], efficiencies, color='#2ca02c', width=0.55, alpha=0.85, edgecolor='#1b611b')

    for bar, eff in zip(bars, efficiencies):
        yval = bar.get_height()
        ax.text(bar.get_x() + bar.get_width() / 2.0, yval + 1.5, f"{eff:.1f}%", ha='center', va='bottom',
                fontsize=9, fontweight='bold', color='#1b611b')

    ax.set_ylim(0, max(120, max(efficiencies) + 15))
    ax.set_title(f'Hiệu suất song song (Parallel Efficiency) - Tập dữ liệu: {dataset_name}', fontsize=12, fontweight='bold', pad=12)
    ax.set_xlabel('Số luồng xử lý (Threads)', fontsize=11, fontweight='bold')
    ax.set_ylabel('Hiệu suất ($E_p = S_p / p$ %)', fontsize=11, fontweight='bold')
    ax.grid(True, axis='y', linestyle=':', alpha=0.6)
    ax.legend(frameon=True, facecolor='white', framealpha=0.9)

    out_file = os.path.join(out_dir, f'chart_efficiency_{dataset_name}.png')
    plt.savefig(out_file, bbox_inches='tight')
    plt.close()
    return out_file


def plot_execution_time(dataset_name, rows, out_dir):
    # Sắp xếp cấu hình: Tuần tự trước, rồi song song tăng dần theo threads
    sorted_rows = sorted(rows, key=lambda x: (0 if 'Tuần tự' in x['Configuration'] else 1, x['Threads']))

    labels = []
    times = []
    errors = []

    for r in sorted_rows:
        if 'Tuần tự' in r['Configuration']:
            labels.append('Tuần tự (1L)')
        else:
            labels.append(f"{r['Threads']} luồng")
        times.append(r['AvgTimeMs'])
        errors.append(r['StdDevMs'])

    fig, ax = plt.subplots(figsize=(9, 5), dpi=300)
    x_pos = range(len(labels))
    bars = ax.bar(x_pos, times, yerr=errors, capsize=5, color='#ff7f0e', alpha=0.85, edgecolor='#b85a07', width=0.55)

    for bar, t, err in zip(bars, times, errors):
        yval = bar.get_height()
        label_text = f"{t:.1f} ms"
        if err > 0:
            label_text += f"\n(±{err:.1f})"
        ax.text(bar.get_x() + bar.get_width() / 2.0, yval + (max(times) * 0.02), label_text,
                ha='center', va='bottom', fontsize=8.5, fontweight='bold', color='#8c3f00')

    ax.set_xticks(x_pos)
    ax.set_xticklabels(labels, fontsize=10)
    ax.set_title(f'Thời gian thực thi trung bình và độ lệch chuẩn - Tập dữ liệu: {dataset_name}', fontsize=12, fontweight='bold', pad=12)
    ax.set_xlabel('Cấu hình thực thi', fontsize=11, fontweight='bold')
    ax.set_ylabel('Thời gian thực thi ($T_p$, mili giây)', fontsize=11, fontweight='bold')
    ax.set_ylim(0, max(times) * 1.25)
    ax.grid(True, axis='y', linestyle=':', alpha=0.6)

    out_file = os.path.join(out_dir, f'chart_time_{dataset_name}.png')
    plt.savefig(out_file, bbox_inches='tight')
    plt.close()
    return out_file


def plot_dashboard(dataset_name, rows, out_dir):
    """
    Tạo biểu đồ tổng hợp 4 trong 1 (Dashboard) chuyên nghiệp cho bài báo cáo
    """
    par_rows = [r for r in rows if r['Threads'] >= 1 and 'Song song' in r['Configuration']]
    if not par_rows:
        par_rows = rows
    par_rows.sort(key=lambda x: x['Threads'])

    threads = [r['Threads'] for r in par_rows]
    speedups = [r['Speedup'] for r in par_rows]
    efficiencies = [r['EfficiencyPercent'] for r in par_rows]
    throughput_pts = [r['ThroughputMPointsSec'] for r in par_rows]
    throughput_mb = [r['ThroughputMBSec'] for r in par_rows]
    times = [r['AvgTimeMs'] for r in par_rows]
    std_devs = [r['StdDevMs'] for r in par_rows]

    fig, axes = plt.subplots(2, 2, figsize=(14, 10), dpi=300)
    fig.suptitle(f'TỔNG HỢP HIỆU NĂNG PHÂN CỤM K-MEANS MAPREDUCE (JAVA THUẦN)\nTập dữ liệu: {dataset_name}',
                 fontsize=14, fontweight='bold', y=0.98)

    # 1. Biểu đồ Thời gian thực thi
    ax1 = axes[0, 0]
    ax1.plot(threads, times, 's-', color='#d62728', linewidth=2, markersize=7, label='Thời gian $T_p$')
    ax1.fill_between(threads, [t - e for t, e in zip(times, std_devs)], [t + e for t, e in zip(times, std_devs)],
                     color='#d62728', alpha=0.15, label='Vùng sai lệch ±StdDev')
    for t, tm in zip(threads, times):
        ax1.annotate(f"{tm:.1f}ms", (t, tm), textcoords="offset points", xytext=(0, 8), ha='center', fontsize=8.5, fontweight='bold')
    ax1.set_title('(A) Thời gian thực thi trung bình theo số luồng', fontsize=11, fontweight='bold')
    ax1.set_xlabel('Số luồng (Threads)')
    ax1.set_ylabel('Thời gian (ms)')
    ax1.set_xticks(threads)
    ax1.grid(True, linestyle=':', alpha=0.6)
    ax1.legend(loc='upper right')

    # 2. Biểu đồ Speedup
    ax2 = axes[0, 1]
    max_t = max(threads) if threads else 16
    ax2.plot([1, max_t], [1, max_t], 'k--', alpha=0.6, label='Tuyến tính lý tưởng ($S=p$)')
    ax2.plot(threads, speedups, 'o-', color='#1f77b4', linewidth=2.5, markersize=8, label='Speedup thực nghiệm ($S_p$)')
    for t, s in zip(threads, speedups):
        ax2.annotate(f"{s:.2f}x", (t, s), textcoords="offset points", xytext=(0, 8), ha='center', fontsize=8.5, fontweight='bold')
    ax2.set_title('(B) Hệ số gia tốc (Speedup)', fontsize=11, fontweight='bold')
    ax2.set_xlabel('Số luồng (Threads)')
    ax2.set_ylabel('Speedup ($S_p$)')
    ax2.set_xticks(threads)
    ax2.grid(True, linestyle=':', alpha=0.6)
    ax2.legend(loc='upper left')

    # 3. Biểu đồ Hiệu suất
    ax3 = axes[1, 0]
    ax3.axhline(100, color='r', linestyle='--', alpha=0.6, label='Lý tưởng 100%')
    bars = ax3.bar([str(t) for t in threads], efficiencies, color='#2ca02c', width=0.55, alpha=0.85, edgecolor='#1b611b')
    for bar, eff in zip(bars, efficiencies):
        ax3.text(bar.get_x() + bar.get_width() / 2.0, bar.get_height() + 1.5, f"{eff:.1f}%",
                 ha='center', va='bottom', fontsize=8.5, fontweight='bold')
    ax3.set_ylim(0, max(120, max(efficiencies) + 15))
    ax3.set_title('(C) Hiệu suất song song (Parallel Efficiency)', fontsize=11, fontweight='bold')
    ax3.set_xlabel('Số luồng (Threads)')
    ax3.set_ylabel('Hiệu suất ($E_p$ %)')
    ax3.grid(True, axis='y', linestyle=':', alpha=0.6)
    ax3.legend(loc='upper right')

    # 4. Biểu đồ Thông lượng xử lý
    ax4 = axes[1, 1]
    line1 = ax4.plot(threads, throughput_pts, 'D-', color='#9467bd', linewidth=2, markersize=7, label='M điểm / giây')
    ax4.set_xlabel('Số luồng (Threads)')
    ax4.set_ylabel('Thông lượng (Triệu điểm / giây)', color='#9467bd', fontweight='bold')
    ax4.tick_params(axis='y', labelcolor='#9467bd')
    ax4.set_xticks(threads)
    ax4.grid(True, linestyle=':', alpha=0.6)

    # Trục phụ y2 cho băng thông đọc (MB/s)
    ax4_sub = ax4.twinx()
    line2 = ax4_sub.plot(threads, throughput_mb, '^-', color='#8c564b', linewidth=2, markersize=7, label='Băng thông đọc (MB/s)')
    ax4_sub.set_ylabel('Băng thông đọc đĩa (MB/s)', color='#8c564b', fontweight='bold')
    ax4_sub.tick_params(axis='y', labelcolor='#8c564b')

    lines = line1 + line2
    labels_combined = [l.get_label() for l in lines]
    ax4.legend(lines, labels_combined, loc='upper left')
    ax4.set_title('(D) Thông lượng xử lý và Băng thông đọc đĩa', fontsize=11, fontweight='bold')

    plt.tight_layout(rect=[0, 0.03, 1, 0.95])
    out_file = os.path.join(out_dir, f'chart_dashboard_{dataset_name}.png')
    plt.savefig(out_file, bbox_inches='tight')
    plt.close()
    return out_file


def main():
    parser = argparse.ArgumentParser(description='Vẽ biểu đồ hiệu năng K-means MapReduce từ results.csv')
    parser.add_argument('--input', default='results.csv', help='Đường dẫn tệp results.csv (mặc định: results.csv)')
    parser.add_argument('--output-dir', default='charts', help='Thư mục lưu ảnh biểu đồ (mặc định: charts)')
    args = parser.parse_args()

    os.makedirs(args.output_dir, exist_ok=True)
    records = load_data(args.input)

    if not records:
        print("[LỖI] Không có dữ liệu trong tệp kết quả!")
        return

    grouped = group_by_dataset(records)
    print(f">>> Đã đọc thành công {len(records)} dòng kết quả từ: {args.input}")
    print(f">>> Tìm thấy {len(grouped)} tập dữ liệu: {list(grouped.keys())}")

    generated_charts = []
    for ds_name, rows in grouped.items():
        clean_name = ds_name.replace(' ', '_').replace(',', '')
        print(f"\n--- Đang vẽ biểu đồ cho tập dữ liệu: {ds_name} ---")
        f1 = plot_speedup(clean_name, rows, args.output_dir)
        f2 = plot_efficiency(clean_name, rows, args.output_dir)
        f3 = plot_execution_time(clean_name, rows, args.output_dir)
        f4 = plot_dashboard(clean_name, rows, args.output_dir)
        generated_charts.extend([f1, f2, f3, f4])
        print(f"  + Đã xuất: {f1}")
        print(f"  + Đã xuất: {f2}")
        print(f"  + Đã xuất: {f3}")
        print(f"  + Đã xuất: {f4}")

    print("\n================================================================================")
    print(f"HOÀN TẤT! Đã tạo thành công {len(generated_charts)} biểu đồ trong thư mục: {os.path.abspath(args.output_dir)}")
    print("================================================================================")


if __name__ == '__main__':
    main()

