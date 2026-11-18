import java.util.List;
import java.util.concurrent.Callable;

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
