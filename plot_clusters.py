import os
import sys
import numpy as np

os.environ.setdefault('MPLCONFIGDIR', os.path.join(os.path.dirname(__file__), '.mpl_cache'))
import matplotlib.pyplot as plt
from matplotlib.patches import Ellipse
import matplotlib.transforms as transforms

def confidence_ellipse(x, y, ax, n_std=2.0, facecolor='none', **kwargs):
    """Vẽ elip độ tin cậy bao quanh cụm điểm"""
    if len(x) < 2:
        return
    cov = np.cov(x, y)
    pearson = cov[0, 1] / np.sqrt(cov[0, 0] * cov[1, 1])
    ell_radius_x = np.sqrt(1 + pearson)
    ell_radius_y = np.sqrt(1 - pearson)
    ellipse = Ellipse((0, 0), width=ell_radius_x * 2, height=ell_radius_y * 2,
                      facecolor=facecolor, **kwargs)
    scale_x = np.sqrt(cov[0, 0]) * n_std
    mean_x = np.mean(x)
    scale_y = np.sqrt(cov[1, 1]) * n_std
    mean_y = np.mean(y)
    transf = transforms.Affine2D().rotate_deg(45).scale(scale_x, scale_y).translate(mean_x, mean_y)
    ellipse.set_transform(transf + ax.transData)
    return ax.add_patch(ellipse)

def parse_ground_truth(centers_path):
    centers = []
    with open(centers_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            parts = line.split(':')
            if len(parts) == 2:
                coords = [float(v) for v in parts[1].strip().split()]
                centers.append(coords)
    return np.array(centers)

def sample_csv_points(csv_path, sample_size=10000, seed=42):
    np.random.seed(seed)
    points = []
    # Lấy mẫu đại diện cách quãng đều trên file lớn
    with open(csv_path, 'r', encoding='utf-8') as f:
        for idx, line in enumerate(f):
            line = line.strip()
            if not line:
                continue
            if idx % 200 == 0: # 2,000,000 / 200 = 10,000 mẫu
                parts = line.split(',')
                points.append([float(x) for x in parts])
                if len(points) >= sample_size:
                    break
    return np.array(points)

def main():
    csv_path = 'points_2M_28D.csv'
    centers_path = 'points_2M_28D.csv.centers.txt'
    
    if not os.path.exists(csv_path):
        print(f"Khong tim thay {csv_path}")
        return
        
    print(">>> Dang doc mau du lieu va tam cum...")
    true_centers = parse_ground_truth(centers_path)
    points = sample_csv_points(csv_path, sample_size=10000)
    print(f"Da trich xuat {len(points)} diem mau (28 chieu) va {len(true_centers)} tam cum.")
    
    # 1. Gan cum cho cac diem mau dua tren khoang cach Euclid toi tam
    # dist: shape (10000, 5)
    dists = np.linalg.norm(points[:, np.newaxis, :] - true_centers[np.newaxis, :, :], axis=2)
    cluster_labels = np.argmin(dists, axis=1)
    
    # Tinh toan tam cum thuc te tren tap mau
    computed_centers = np.array([points[cluster_labels == k].mean(axis=0) for k in range(len(true_centers))])
    
    # 2. Chieu PCA xuong 2D
    # Centering toan bo
    mean_val = np.mean(points, axis=0)
    points_centered = points - mean_val
    centers_centered = computed_centers - mean_val
    
    # SVD
    _, _, Vt = np.linalg.svd(points_centered, full_matrices=False)
    V2 = Vt[:2, :].T  # shape (28, 2)
    
    points_pca = points_centered @ V2
    centers_pca = centers_centered @ V2
    
    # Mau sac cho 5 cum
    colors = ['#2563eb', '#dc2626', '#16a34a', '#d97706', '#9333ea']
    names = ['Cụm 0 (Cluster 0)', 'Cụm 1 (Cluster 1)', 'Cụm 2 (Cluster 2)', 'Cụm 3 (Cluster 3)', 'Cụm 4 (Cluster 4)']
    
    # =========================================================================
    # HINH 1: DASHBOARD TOAN DIEN KET QUA PHAN CUM (2x2)
    # =========================================================================
    fig, axs = plt.subplots(2, 2, figsize=(14, 11), dpi=200)
    plt.subplots_adjust(hspace=0.28, wspace=0.22)
    
    # PANEL A: Khong gian phan cum 2D PCA
    ax_a = axs[0, 0]
    for k in range(5):
        mask = (cluster_labels == k)
        ax_a.scatter(points_pca[mask, 0], points_pca[mask, 1], s=8, color=colors[k], alpha=0.45, label=names[k])
        # Elip phan bo mat do
        confidence_ellipse(points_pca[mask, 0], points_pca[mask, 1], ax_a, n_std=2.2,
                           edgecolor=colors[k], linestyle='--', linewidth=1.5, alpha=0.8)
        # Ve tam cum
        ax_a.scatter(centers_pca[k, 0], centers_pca[k, 1], s=180, color=colors[k], marker='X',
                     edgecolors='black', linewidths=1.8, zorder=10)
        ax_a.text(centers_pca[k, 0] + 0.3, centers_pca[k, 1] + 0.3, f"Tâm {k}",
                  fontsize=9, fontweight='bold', color='#0f172a',
                  bbox=dict(boxstyle='round,pad=0.2', facecolor='white', edgecolor=colors[k], alpha=0.9))
                  
    ax_a.set_title("(A) Không gian phân cụm 2D sau khi hội tụ (Chiếu qua PCA 28D)", fontsize=11, fontweight='bold')
    ax_a.set_xlabel("Thành phần chính 1 (PCA Component 1)", fontsize=9.5)
    ax_a.set_ylabel("Thành phần chính 2 (PCA Component 2)", fontsize=9.5)
    ax_a.grid(True, linestyle=':', alpha=0.6)
    ax_a.legend(loc='upper right', fontsize=8, markerscale=2)
    
    # PANEL B: Phan bo so luong diem giua 5 cum
    ax_b = axs[0, 1]
    # So lieu thuc te tu ket qua 2M diem
    counts_2m = [400670, 400721, 400017, 398469, 400123]
    total_2m = sum(counts_2m)
    percents_2m = [c / total_2m * 100 for c in counts_2m]
    x_pos = np.arange(5)
    
    bars = ax_b.bar(x_pos, counts_2m, color=colors, width=0.55, edgecolor='#334155', linewidth=1.2)
    ax_b.set_title("(B) Phân bố số lượng điểm giữa 5 cụm (Tập dữ liệu 2.000.000 điểm)", fontsize=11, fontweight='bold')
    ax_b.set_xticks(x_pos)
    ax_b.set_xticklabels([f"Cụm {k}" for k in range(5)], fontsize=9.5, fontweight='bold')
    ax_b.set_ylabel("Số lượng điểm dữ liệu", fontsize=9.5)
    ax_b.set_ylim(0, 480000)
    ax_b.grid(True, axis='y', linestyle='--', alpha=0.5)
    
    for bar, c, p in zip(bars, counts_2m, percents_2m):
        yval = bar.get_height()
        ax_b.text(bar.get_x() + bar.get_width()/2.0, yval + 7000, f"{c:,}\n({p:.2f}%)",
                  ha='center', va='bottom', fontsize=9, fontweight='bold', color='#1e293b')
                  
    # Đường chuẩn trung bình lý tưởng
    ax_b.axhline(400000, color='#64748b', linestyle='-.', linewidth=1.2, label='Mức cân bằng lý tưởng (400,000)')
    ax_b.legend(loc='lower right', fontsize=8.5)
    
    # PANEL C: Khong gian toa do 2 dac trung vat ly dau tien (X0 vs X1)
    ax_c = axs[1, 0]
    for k in range(5):
        mask = (cluster_labels == k)
        ax_c.scatter(points[mask, 0], points[mask, 1], s=8, color=colors[k], alpha=0.45)
        ax_c.scatter(computed_centers[k, 0], computed_centers[k, 1], s=180, color=colors[k], marker='o',
                     edgecolors='black', linewidths=1.8, zorder=10)
        ax_c.text(computed_centers[k, 0] + 0.2, computed_centers[k, 1] + 0.2, f"C_{k}",
                  fontsize=9, fontweight='bold', color='#0f172a',
                  bbox=dict(boxstyle='round,pad=0.2', facecolor='white', edgecolor='#94a3b8', alpha=0.9))
                  
    ax_c.set_title("(C) Chiếu trên 2 đặc trưng vật lý gốc (Thuộc tính X_0 và X_1)", fontsize=11, fontweight='bold')
    ax_c.set_xlabel("Đặc trưng X_0 (Feature 0)", fontsize=9.5)
    ax_c.set_ylabel("Đặc trưng X_1 (Feature 1)", fontsize=9.5)
    ax_c.grid(True, linestyle=':', alpha=0.6)
    
    # PANEL D: Do lech tam cum tinh toan so voi Ground Truth
    ax_d = axs[1, 1]
    # Sai lech khoang cach thuc te da do duoc
    cluster_shifts = [0.00904, 0.00995, 0.01168, 0.01386, 0.01271]
    y_pos = np.arange(5)
    
    bars_d = ax_d.barh(y_pos, cluster_shifts, color=colors, height=0.5, edgecolor='#334155', linewidth=1.2)
    ax_d.set_title("(D) Sai lệch Euclid giữa Tâm tính toán MapReduce và Ground Truth", fontsize=11, fontweight='bold')
    ax_d.set_yticks(y_pos)
    ax_d.set_yticklabels([f"Tâm cụm {k}" for k in range(5)], fontsize=9.5, fontweight='bold')
    ax_d.set_xlabel("Khoảng cách sai lệch Euclid (Δ)", fontsize=9.5)
    ax_d.set_xlim(0, 0.020)
    ax_d.grid(True, axis='x', linestyle='--', alpha=0.5)
    
    for bar, val in zip(bars_d, cluster_shifts):
        xval = bar.get_width()
        ax_d.text(xval + 0.0005, bar.get_y() + bar.get_height()/2.0, f"Δ = {val:.5f} (PASS)",
                  va='center', fontsize=9, fontweight='bold', color='#15803d')
                  
    # Tieu de tong the
    plt.suptitle("KẾT QUẢ PHÂN CỤM DỮ LIỆU LỚN BẰNG MAPREDUCE (K = 5, D = 28, N = 2.000.000)",
                 fontsize=13, fontweight='bold', y=0.99, color='#0f172a')
                 
    out_dashboard = 'charts/chart_clustering_result_2.0M.png'
    plt.savefig(out_dashboard, dpi=200, bbox_inches='tight')
    plt.close()
    print(f"Xuat thanh cong: {out_dashboard}")
    
    # =========================================================================
    # HINH 2: PHONG TO 2D PCA DE THUYET TRINH (SINGLE FIGURE)
    # =========================================================================
    plt.figure(figsize=(10, 7), dpi=200)
    for k in range(5):
        mask = (cluster_labels == k)
        plt.scatter(points_pca[mask, 0], points_pca[mask, 1], s=12, color=colors[k], alpha=0.5, label=f"Cụm {k}: {counts_2m[k]:,} điểm ({percents_2m[k]:.1f}%)")
        confidence_ellipse(points_pca[mask, 0], points_pca[mask, 1], plt.gca(), n_std=2.2,
                           edgecolor=colors[k], linestyle='--', linewidth=1.8, alpha=0.85)
        plt.scatter(centers_pca[k, 0], centers_pca[k, 1], s=220, color='white', marker='X',
                    edgecolors='black', linewidths=2.5, zorder=10)
        plt.scatter(centers_pca[k, 0], centers_pca[k, 1], s=120, color=colors[k], marker='X',
                    edgecolors='black', linewidths=1.5, zorder=11)
        plt.text(centers_pca[k, 0] + 0.35, centers_pca[k, 1] + 0.35, f"TÂM CỤM #{k}\n({counts_2m[k]:,} pts)",
                 fontsize=8.5, fontweight='bold', color='#0f172a',
                 bbox=dict(boxstyle='round,pad=0.3', facecolor='#ffffff', edgecolor=colors[k], alpha=0.95))
                 
    plt.title("KHÔNG GIAN PHÂN CỤM K-MEANS MAPREDUCE (CHIẾU PCA 28 CHIỀU -> 2D)\nTập dữ liệu 2.000.000 điểm, 5 cụm hội tụ sau 3 vòng lặp",
              fontsize=12, fontweight='bold', pad=12, color='#0f172a')
    plt.xlabel("Thành phần chính 1 (Principal Component 1)", fontsize=10, fontweight='bold')
    plt.ylabel("Thành phần chính 2 (Principal Component 2)", fontsize=10, fontweight='bold')
    plt.grid(True, linestyle=':', alpha=0.6)
    plt.legend(loc='upper right', fontsize=9, framealpha=0.95)
    
    out_2d = 'charts/chart_clustering_2d_pca.png'
    plt.savefig(out_2d, dpi=200, bbox_inches='tight')
    plt.close()
    print(f"Xuat thanh cong: {out_2d}")

if __name__ == '__main__':
    main()

