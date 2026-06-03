package Bears.Experiments.Calibration;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Result of evaluating one {@link CandidatePoint} across all replicates:
 * the mean loss, its per-component breakdown, and the per-replicate
 * stationarity summaries used to derive it.
 */
public final class EvaluationResult {

    public final CandidatePoint point;
    public final double meanLoss;
    public final List<StationarityTargets.Score> replicateScores;
    public final List<StationarityTargets.ReplicateSummary> replicateSummaries;
    public final long elapsedMillis;

    public EvaluationResult(CandidatePoint point,
                            double meanLoss,
                            List<StationarityTargets.Score> replicateScores,
                            List<StationarityTargets.ReplicateSummary> replicateSummaries,
                            long elapsedMillis) {
        this.point = point;
        this.meanLoss = meanLoss;
        this.replicateScores = Collections.unmodifiableList(replicateScores);
        this.replicateSummaries = Collections.unmodifiableList(replicateSummaries);
        this.elapsedMillis = elapsedMillis;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "loss=%.4f %s (%.1fs)",
                meanLoss, point, elapsedMillis / 1000.0);
    }
}
