import java.util.List;
import java.util.concurrent.Callable;

/**
 * ReduceTask: Tác vụ rút gọn (Reducer) chạy song song cho từng cụm (hoặc nhóm cụm).
 *
 * Nhiệm vụ:
 * 1. Nhận danh sách PartialResult từ tất cả các Mapper sau pha Shuffle.
 * 2. Gom tổng vector và tổng số lượng điểm của cụm được phân công.
 * 3. Tính toán trọng tâm mới: c'_k = sumVector / totalPoints.
 * 4. Xử lý cụm rỗng (Empty Cluster): nếu không có điểm nào rơi vào cụm, giữ nguyên tâm cũ.
 * 5. Tính độ dịch chuyển không gian (Euclidean shift) giữa tâm mới và tâm cũ.
 */
public class ReduceTask implements Callable<ReduceTask.ClusterUpdate> {
    private final int clusterId;
    private final List<PartialResult> mapperResults;
    private final double[] oldCentroid;
    private final int dim;

    public static class ClusterUpdate {
        private final int clusterId;
        private final double[] newCentroid;
        private final long pointCount;
        private final double clusterSSE;
        private final double shift;

        public ClusterUpdate(int clusterId, double[] newCentroid, long pointCount, double clusterSSE, double shift) {
            this.clusterId = clusterId;
            this.newCentroid = newCentroid;
            this.pointCount = pointCount;
            this.clusterSSE = clusterSSE;
            this.shift = shift;
        }

        public int getClusterId() {
            return clusterId;
        }

        public double[] getNewCentroid() {
            return newCentroid;
        }

        public long getPointCount() {
            return pointCount;
        }

        public double getClusterSSE() {
            return clusterSSE;
        }

        public double getShift() {
            return shift;
        }
    }

    public ReduceTask(int clusterId, List<PartialResult> mapperResults, double[] oldCentroid, int dim) {
        this.clusterId = clusterId;
        this.mapperResults = mapperResults;
        this.oldCentroid = oldCentroid;
        this.dim = dim;
    }

    @Override
    public ClusterUpdate call() {
        double[] totalSum = new double[dim];
        long totalPoints = 0;
        double clusterSSE = 0.0;

        // Gom dữ liệu từ các mapper tương ứng với clusterId
        for (PartialResult pr : mapperResults) {
            totalPoints += pr.getClusterCounts()[clusterId];
            clusterSSE += pr.getClusterSSE()[clusterId];
            double[] mapperSum = pr.getClusterSums()[clusterId];
            for (int d = 0; d < dim; d++) {
                totalSum[d] += mapperSum[d];
            }
        }

        double[] newCentroid = new double[dim];
        double shiftSq = 0.0;

        if (totalPoints > 0) {
            for (int d = 0; d < dim; d++) {
                newCentroid[d] = totalSum[d] / totalPoints;
                double diff = newCentroid[d] - oldCentroid[d];
                shiftSq += diff * diff;
            }
        } else {
            // Cụm rỗng: giữ nguyên tâm cũ để tránh lỗi chia cho 0
            System.arraycopy(oldCentroid, 0, newCentroid, 0, dim);
            shiftSq = 0.0;
        }

        double shift = Math.sqrt(shiftSq);
        return new ClusterUpdate(clusterId, newCentroid, totalPoints, clusterSSE, shift);
    }
}

